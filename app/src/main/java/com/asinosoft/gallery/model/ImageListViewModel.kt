package com.asinosoft.gallery.model

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumCategory
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.Application
import com.asinosoft.gallery.data.ApplicationDao
import com.asinosoft.gallery.data.Filter
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.MediaDao
import com.asinosoft.gallery.data.MediaService
import com.asinosoft.gallery.data.launchAndCatch
import com.asinosoft.gallery.data.storage.StorageDao
import com.asinosoft.gallery.data.storage.StorageService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

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

@HiltViewModel
class ImageListViewModel @Inject constructor(
    state: SavedStateHandle,
    albumDao: AlbumDao,
    mediaDao: MediaDao,
    applicationDao: ApplicationDao,
    private val mediaService: MediaService,
    private val storageDao: StorageDao,
    private val storageService: StorageService,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    val albumId: Long? = state["albumId"]

    val album = MutableStateFlow<Album?>(null)

    val selection = MutableStateFlow<Set<Long>>(setOf())

    val categories = albumDao.getAlbumCategories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

    val isFetching = storageService.isFetching

    private val allImages: StateFlow<List<Media>> = (
            albumId?.let { albumDao.getMediaInAlbum(albumId) }
                ?: mediaDao.getImages()
            ).stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private val applications = MutableStateFlow<List<Application>>(listOf())
    private val activeFilters = MutableStateFlow<Set<String>>(setOf())

    val activeFilterPackages: StateFlow<Set<String>> = activeFilters

    val activeDateFilter = MutableStateFlow<DateFilter?>(null)
    private var allFilters = MutableStateFlow<List<Filter>>(listOf())

    val images: StateFlow<List<Media>> =
        combine(allImages, activeFilters, activeDateFilter) { images, filters, dateFilter ->
            images.filter { image ->
                (filters.isEmpty() || filters.contains(image.owner)) &&
                        (dateFilter?.year == null || image.date.year == dateFilter.year) &&
                        (dateFilter?.month == null || image.date.monthValue == dateFilter.month) &&
                        (dateFilter?.day == null || image.date.dayOfMonth == dateFilter.day)
            }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    val filters = combine(allFilters, activeDateFilter, allImages) { filters, dateFilter, images ->
        val apps = images.filter { image ->
            (dateFilter?.year == null || image.date.year == dateFilter.year) &&
                    (dateFilter?.month == null || image.date.monthValue == dateFilter.month) &&
                    (dateFilter?.day == null || image.date.dayOfMonth == dateFilter.day)
        }.map { it.owner }.toSet()
        filters.filter { apps.contains(it.application.pkg) }
    }

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
    }

    init {
        viewModelScope.launch {
            albumId?.let { albumId ->
                val value = albumDao.getAlbumById(albumId)
                album.emit(value)
            }


            var lastOwners: List<String>? = null
            allImages.collect { images ->
                val ownersInOrder = images.mapNotNull { it.owner }.distinct()
                if (ownersInOrder != lastOwners) {
                    lastOwners = ownersInOrder
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val fetchedApps = applicationDao.getApplications(ownersInOrder.toSet())
                            .associateBy { it.pkg }
                        applications.value = ownersInOrder.mapNotNull { fetchedApps[it] }
                        allFilters.emit(
                            applications.value.map {
                                Filter(
                                    it,
                                    activeFilters.value.isEmpty() or activeFilters.value.contains(it.pkg)
                                )
                            }
                        )
                    }
                }
            }
        }
    }

    fun clearFilters() = viewModelScope.launch {
        activeFilters.emit(setOf())
    }


    fun setDateFilter(filter: DateFilter?) {
        activeDateFilter.value = filter
    }

    fun clearDateFilter() {
        activeDateFilter.value = null
    }

    fun toggleFilter(filter: Filter) = viewModelScope.launch {
        val newFilters = activeFilters.value.toMutableSet()
        if (activeFilters.value.contains(filter.application.pkg)) {
            newFilters.remove(filter.application.pkg)
        } else {
            newFilters.add(filter.application.pkg)
        }
        activeFilters.emit(newFilters)
        allFilters.emit(
            applications.value.map {
                Filter(
                    it,
                    activeFilters.value.isEmpty() or newFilters.contains(it.pkg)
                )
            }
        )
    }

    fun fetch() = viewModelScope.launchAndCatch {
        storageDao.getAccounts().first().forEach {
            storageService.fetch(it)
        }
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

    fun addSelectionToAlbum(albumId: Long) = viewModelScope.launchAndCatch {
        mediaService.addToAlbum(selection.value, albumId)
        clearSelection()
    }

    fun addSelectionToNewAlbum(name: String, category: AlbumCategory) =
        viewModelScope.launchAndCatch {
            mediaService.addToNewAlbum(selection.value, name, category)
            clearSelection()
        }

    fun removeSelectionFromAlbum(albumId: Long) = viewModelScope.launchAndCatch {
        mediaService.removeFromAlbum(selection.value, albumId)
        clearSelection()
    }
}
