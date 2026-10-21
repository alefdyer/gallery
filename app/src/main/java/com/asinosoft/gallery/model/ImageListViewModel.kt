package com.asinosoft.gallery.model

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumCategory
import com.asinosoft.gallery.data.AlbumWithCover
import com.asinosoft.gallery.data.AppAnalytics
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.Application
import com.asinosoft.gallery.data.ApplicationDao
import com.asinosoft.gallery.data.Filter
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.MediaDao
import com.asinosoft.gallery.data.MediaService
import com.asinosoft.gallery.data.launchAndCatch
import com.asinosoft.gallery.data.storage.MediaStatistics
import com.asinosoft.gallery.data.storage.StorageDao
import com.asinosoft.gallery.data.storage.StorageProviderRegistry
import com.asinosoft.gallery.data.storage.StorageService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import androidx.core.content.edit

data class DateFilter(
    val year: Int? = null,
    val month: Int? = null,
    val day: Int? = null
) {
    override fun toString(): String = "$year-$month-$day"
}

fun String?.toDateFilter() = this?.split("-")?.let {

    DateFilter(
        it.getOrNull(0)?.toIntOrNull(),
        it.getOrNull(1)?.toIntOrNull(),
        it.getOrNull(2)?.toIntOrNull()
    )
}

enum class MediaType { PHOTO, VIDEO }

enum class DateFilterSource { CALENDAR, SLIDER }

fun MediaType?.matches(media: Media) = when (this) {
    null -> true
    MediaType.PHOTO -> null == media.video
    MediaType.VIDEO -> null != media.video
}

private const val NO_APPS = ""
private const val RECENT_DATES_PREFS = "date_filters"
private const val RECENT_DATES_KEY = "recent"
private const val MAX_RECENT_DATES = 10

@HiltViewModel
class ImageListViewModel @Inject constructor(
    state: SavedStateHandle,
    private val albumDao: AlbumDao,
    mediaDao: MediaDao,
    applicationDao: ApplicationDao,
    storageProviderRegistry: StorageProviderRegistry,
    private val mediaService: MediaService,
    private val storageDao: StorageDao,
    private val storageService: StorageService,
    private val analytics: AppAnalytics,
    @param:ApplicationContext private val context: Context
) : MediaViewModel(storageProviderRegistry) {
    val storageId: Long = state["storageId"] ?: 1

    val albumId: Long? = state["albumId"]

    val favoritesOnly: Boolean = state["favorites"] ?: false

    val showAds: Boolean = (albumId != null)

    val album = MutableStateFlow<Album?>(null)
    val albumCategory = MutableStateFlow<AlbumCategory?>(null)

    // Соседние альбомы для листания свайпом: только своя категория и с первой
    // композиции экрана, а не после перехода навигации и загрузки всех категорий —
    // иначе свайп не работает, пока не подгрузятся верхние панели.
    // Порядок — как на вкладке «Альбомы».
    val siblingAlbums: StateFlow<List<Album>> = (
            albumId?.let { albumDao.getCategoryAlbums(it) } ?: flowOf(emptyList())
            )
        .map { albums ->
            albums
                .sortedWith(
                    compareByDescending<AlbumWithCover> { it.album.date }
                        .thenBy { it.album.name }
                )
                .map { it.album }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val selection = MutableStateFlow<Set<Long>>(setOf())

    val categories = albumDao.getAlbumCategories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    val isFetching = storageService.isFetching

    private val allImages: StateFlow<List<Media>> = (
            if (favoritesOnly) {
                mediaDao.getFavorites()
            } else {
                albumId?.let { albumDao.getMediaInAlbum(albumId) } ?: mediaDao.getImages(storageId)
            }
            ).stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val hasMedia: StateFlow<Boolean> = allImages.map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val applications = MutableStateFlow<Map<Application?, MediaStatistics>>(mapOf())
    private val activeFilters = MutableStateFlow<Set<String>>(setOf())

    val activeFilterPackages: StateFlow<Set<String>> = activeFilters

    val activeDateFilter = MutableStateFlow<DateFilter?>(null)
    private var allFilters = MutableStateFlow<List<Filter>>(listOf())

    val showPhotos = MutableStateFlow(true)
    val showVideos = MutableStateFlow(true)

    private val filteredImages: StateFlow<List<Media>> =
        combine(allImages, activeFilters, activeDateFilter) { images, filters, dateFilter ->
            images.filter { image ->
                (filters.isEmpty() || filters.contains(image.owner)) &&
                        (dateFilter?.year == null || image.date.year == dateFilter.year) &&
                        (dateFilter?.month == null || image.date.monthValue == dateFilter.month) &&
                        (dateFilter?.day == null || image.date.dayOfMonth == dateFilter.day)
            }
        }.flowOn(Dispatchers.Default).stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val images: StateFlow<List<Media>> =
        combine(filteredImages, showPhotos, showVideos) { images, photos, videos ->
            if (photos && videos) images else images.filter { if (null == it.video) photos else videos }
        }.flowOn(Dispatchers.Default).stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val photoCount: StateFlow<Int> = filteredImages.map { list -> list.count { null == it.video } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val videoCount: StateFlow<Int> = filteredImages.map { list -> list.count { null != it.video } }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val filters = combine(allFilters, activeDateFilter, allImages) { filters, dateFilter, images ->
        val apps = images.filter { image ->
            (dateFilter?.year == null || image.date.year == dateFilter.year) &&
                    (dateFilter?.month == null || image.date.monthValue == dateFilter.month) &&
                    (dateFilter?.day == null || image.date.dayOfMonth == dateFilter.day)
        }.groupingBy { it.owner }.eachCount()
        filters.filter { apps.contains(it.application.pkg) }.sortedByDescending { it.statistics }
    }.flowOn(Dispatchers.Default)

    // Группировка: Year -> Month -> Day -> List<Int>
    val dateGroups = combine(activeFilters, allImages) { filters, images ->
        images
            .filter { filters.isEmpty() || filters.contains(it.owner) }
            .groupBy { it.date.year }
            .mapValues { (_, yearImages) ->
                yearImages.groupBy { it.date.monthValue }
                    .mapValues { (_, monthImages) ->
                        monthImages.groupBy { it.date.dayOfMonth }.mapValues { it.value.size }
                    }
            }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        viewModelScope.launch {
            combine(photoCount, videoCount) { photos, videos -> photos > 0 && videos > 0 }
                .collect { hasBothTypes ->
                    if (!hasBothTypes) {
                        showPhotos.value = true
                        showVideos.value = true
                    }
                }
        }

        viewModelScope.launch {
            albumId?.let { albumId ->
                val value = albumDao.getAlbumById(albumId)
                val category = albumDao.getCategoryById(value.categoryId)
                // Категорию ставим первой: в UI она появится одним кадром с названием альбома,
                // а не вторым запросом после него.
                albumCategory.emit(category)
                album.emit(value)
            }


            var lastOwners: Map<String?, MediaStatistics>? = null
            allImages.collect { images ->
                val ownersInOrder = withContext(Dispatchers.Default) {
                    images.groupingBy { it.owner }.fold(MediaStatistics()) { c, media ->
                        MediaStatistics(
                            c.photoCount + if (media.image != null) 1 else 0,
                            c.videoCount + if (media.video != null) 1 else 0
                        )
                    }
                }
                if (ownersInOrder != lastOwners) {
                    lastOwners = ownersInOrder
                    withContext(Dispatchers.IO) {
                        val fetchedApps = applicationDao.getApplications(
                            ownersInOrder.keys.filterNotNull().toSet()
                        )
                            .associateBy { it.pkg }
                        applications.value = lastOwners.mapKeys { fetchedApps[it.key] }
                        allFilters.emit(
                            applications.value.mapNotNull { (app, count) ->
                                app?.let {
                                    Filter(
                                        application = app,
                                        statistics = count,
                                        enabled = activeFilters.value.isEmpty() or activeFilters.value.contains(
                                            app.pkg
                                        )
                                    )
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    fun clearFilters() = viewModelScope.launch {
        applyActiveFilters(setOf())
    }

    fun hideAllFilters() = viewModelScope.launch {
        applyActiveFilters(setOf(NO_APPS))
    }

    fun setFilterEnabled(filter: Filter, enabled: Boolean) = viewModelScope.launch {
        analytics.filterApps()
        val all = applications.value.keys.mapNotNull { it?.pkg }.toSet()
        val current = activeFilters.value.ifEmpty { all } - NO_APPS
        val updated = if (enabled) {
            current + filter.application.pkg
        } else {
            current - filter.application.pkg
        }
        applyActiveFilters(
            when {
                updated.isEmpty() -> setOf(NO_APPS)
                updated.containsAll(all) -> setOf()
                else -> updated
            }
        )
    }

    private suspend fun applyActiveFilters(newFilters: Set<String>) {
        activeFilters.emit(newFilters)
        allFilters.emit(
            applications.value.mapNotNull { (app, count) ->
                app?.let {
                    Filter(app, count, newFilters.isEmpty() || newFilters.contains(it.pkg))
                }
            }
        )
    }


    fun activeMediaType(): MediaType? = when {
        showPhotos.value == showVideos.value -> null
        showVideos.value -> MediaType.VIDEO
        else -> MediaType.PHOTO
    }

    fun togglePhotos() {
        val onlyPhotos = showPhotos.value && !showVideos.value
        showPhotos.value = true
        showVideos.value = onlyPhotos
        analytics.filterCounters()
    }

    fun toggleVideos() {
        val onlyVideos = showVideos.value && !showPhotos.value
        showVideos.value = true
        showPhotos.value = onlyVideos
        analytics.filterCounters()
    }

    fun setDateFilter(filter: DateFilter?) {
        activeDateFilter.value = filter
    }

    fun imagesForDate(filter: DateFilter): List<Media> {
        val owners = activeFilters.value
        return allImages.value.filter { image ->
            (owners.isEmpty() || owners.contains(image.owner)) &&
                    (filter.year == null || image.date.year == filter.year) &&
                    (filter.month == null || image.date.monthValue == filter.month) &&
                    (filter.day == null || image.date.dayOfMonth == filter.day)
        }
    }

    fun albumImages(albumId: Long): Flow<List<Media>> = albumDao.getMediaInAlbum(albumId)

    fun adjacentDateFilter(older: Boolean): DateFilter? {
        val filter = activeDateFilter.value ?: return null
        if (null == filter.year) return null
        val owners = activeFilters.value
        val comparator = compareBy<DateFilter>({ it.year }, { it.month }, { it.day })

        val dates = allImages.value.asSequence()
            .filter { owners.isEmpty() || owners.contains(it.owner) }
            .map {
                when {
                    null != filter.day -> DateFilter(
                        it.date.year,
                        it.date.monthValue,
                        it.date.dayOfMonth
                    )

                    null != filter.month -> DateFilter(it.date.year, it.date.monthValue)
                    else -> DateFilter(it.date.year)
                }
            }
            .distinct()
            .sortedWith(comparator)
            .toList()

        return if (older) {
            dates.lastOrNull { comparator.compare(it, filter) < 0 }
        } else {
            dates.firstOrNull { comparator.compare(it, filter) > 0 }
        }
    }

    fun clearDateFilter() {
        activeDateFilter.value = null
    }

    fun recentDateFilters(): List<DateFilter> =
        recentDatesPrefs.getString(RECENT_DATES_KEY, null)
            ?.split(",")
            ?.filter { it.isNotBlank() }
            ?.mapNotNull { it.toDateFilter()?.takeIf { filter -> null != filter.year } }
            .orEmpty()

    fun pickDateFilter(filter: DateFilter, source: DateFilterSource = DateFilterSource.CALENDAR) {
        setDateFilter(filter)
        val recent = (listOf(filter) + recentDateFilters().filter { it != filter })
            .take(MAX_RECENT_DATES)
        recentDatesPrefs.edit {
            putString(RECENT_DATES_KEY, recent.joinToString(","))
        }
        when (source) {
            DateFilterSource.CALENDAR -> analytics.filterDate()
            DateFilterSource.SLIDER -> analytics.filterDateSlider()
        }
    }

    private val recentDatesPrefs
        get() = context.getSharedPreferences(RECENT_DATES_PREFS, Context.MODE_PRIVATE)

    fun toggleFilter(filter: Filter) = viewModelScope.launch {
        analytics.filterApps()
        val newFilters = activeFilters.value.toMutableSet()
        if (activeFilters.value.contains(filter.application.pkg)) {
            newFilters.remove(filter.application.pkg)
        } else {
            newFilters.add(filter.application.pkg)
        }
        activeFilters.emit(newFilters)
        allFilters.emit(
            applications.value.mapNotNull { (app, count) ->
                app?.let {
                    Filter(app, count, activeFilters.value.isEmpty() or newFilters.contains(it.pkg))
                }
            }
        )
    }

    fun fetch() = viewModelScope.launchAndCatch {
        val storages = storageDao.getAccounts().first()
        storages.forEach {
            storageService.fetch(it)
        }
        if (storages.isNotEmpty()) analytics.scanCompleted()
    }

    fun clearSelection() {
        selection.value = setOf()
    }

    fun setSelection(value: Set<Long>) {
        selection.value = value
    }

    fun toggleSelection(media: Media) {
        selection.value = if (selection.value.contains(media.id)) {
            selection.value - media.id
        } else {
            selection.value + media.id
        }

    }

    fun shareSelection() = viewModelScope.launchAndCatch {
        mediaService.share(selection.value, context)
        selection.value = setOf()
    }

    fun deleteSelection() = viewModelScope.launchAndCatch {
        mediaService.delete(selection.value, context) {
            selection.value = setOf()
        }
    }

    fun addSelectionToFavorites() = viewModelScope.launchAndCatch {
        mediaService.setFavoriteWithRequest(selection.value, true, ::clearSelection)
    }

    fun moveSelectionToPrivate() = viewModelScope.launchAndCatch {
        mediaService.moveToPrivate(selection.value, context, ::clearSelection)
    }

    fun addSelectionToAlbum(albumId: Long, removeFromAlbumId: Long? = null) =
        viewModelScope.launchAndCatch {
            val ids = selection.value
            mediaService.addToAlbum(ids, albumId)
            if (null != removeFromAlbumId && removeFromAlbumId != albumId) {
                mediaService.removeFromAlbum(ids, removeFromAlbumId)
            }
            if (ids.isNotEmpty()) analytics.albumMediaAdded()
            clearSelection()
        }

    fun addSelectionToNewAlbum(
        name: String,
        category: AlbumCategory,
        removeFromAlbumId: Long? = null
    ) = viewModelScope.launchAndCatch {
        val ids = selection.value
        mediaService.addToNewAlbum(ids, name, category)
        removeFromAlbumId?.let { mediaService.removeFromAlbum(ids, it) }
        clearSelection()
    }

    fun addSelectionToNewAlbumInNewCategory(
        name: String,
        categoryName: String,
        removeFromAlbumId: Long? = null
    ) = viewModelScope.launchAndCatch {
        val ids = selection.value
        mediaService.addToNewAlbumInNewCategory(ids, name, categoryName)
        removeFromAlbumId?.let { mediaService.removeFromAlbum(ids, it) }
        clearSelection()
    }

    fun removeSelectionFromAlbum(albumId: Long) = viewModelScope.launchAndCatch {
        mediaService.removeFromAlbum(selection.value, albumId)
        clearSelection()
    }
}
