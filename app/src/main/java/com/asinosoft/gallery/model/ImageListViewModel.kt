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
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
)

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

    val images: StateFlow<List<Media>> = (
            albumId?.let { albumDao.getMediaInAlbum(albumId) }
                ?: mediaDao.getImages()
            ).stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    private lateinit var applications: List<Application>
    private val activeFilters = MutableStateFlow<Set<String>>(setOf())

    val activeFilterPackages: StateFlow<Set<String>> = activeFilters

    val activeDateFilter = MutableStateFlow<DateFilter?>(null)
    val isFolderExplorerOpen = MutableStateFlow(false)
    val expandedFolderNodes = androidx.compose.runtime.mutableStateMapOf<String, Boolean>()
    var treeListIndex = 0
    var treeListOffset = 0

    @OptIn(ExperimentalCoroutinesApi::class)
    var filters = MutableStateFlow<List<Filter>>(listOf())

    val filteredImages: StateFlow<List<Media>> = combine(images, activeFilters, activeDateFilter) { images, filters, dateFilter ->
        var result = if (filters.isEmpty()) images else images.filter { filters.contains(it.owner) }
        if (dateFilter != null) {
            result = result.filter { media ->
                (dateFilter.year == null || media.date.year == dateFilter.year) &&
                (dateFilter.month == null || media.date.monthValue == dateFilter.month) &&
                (dateFilter.day == null || media.date.dayOfMonth == dateFilter.day)
            }
        }
        result
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    init {
        viewModelScope.launch {
            albumId?.let { albumId ->
                val value = albumDao.getAlbumById(albumId)
                album.emit(value)
            }


            var lastOwners: List<String>? = null
            images.collect { images ->
                val ownersInOrder = images.mapNotNull { it.owner }.distinct()
                if (ownersInOrder != lastOwners) {
                    lastOwners = ownersInOrder
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        val fetchedApps = applicationDao.getApplications(ownersInOrder.toSet()).associateBy { it.pkg }
                        applications = ownersInOrder.mapNotNull { fetchedApps[it] }
                        filters.emit(
                            applications.map {
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

    fun setDateFilter(filter: DateFilter?) {
        activeDateFilter.value = filter
        if (filter != null) {
            isFolderExplorerOpen.value = false
        }
    }

    fun getAdjacentDateFilter(direction: Int): DateFilter? {
        val current = activeDateFilter.value ?: return null
        val allImages = images.value
        if (allImages.isEmpty()) return null

        val periods: List<DateFilter> = when {
            current.day != null -> {
                allImages.map { DateFilter(it.date.year, it.date.monthValue, it.date.dayOfMonth) }
                    .distinct()
                    .sortedWith(compareByDescending<DateFilter> { it.year }.thenByDescending { it.month }.thenByDescending { it.day })
            }
            current.month != null -> {
                allImages.map { DateFilter(it.date.year, it.date.monthValue, null) }
                    .distinct()
                    .sortedWith(compareByDescending<DateFilter> { it.year }.thenByDescending { it.month })
            }
            current.year != null -> {
                allImages.map { DateFilter(it.date.year, null, null) }
                    .distinct()
                    .sortedByDescending { it.year }
            }
            else -> return null
        }

        val currentIndex = periods.indexOf(current)
        if (currentIndex != -1) {
            val nextIndex = currentIndex + direction
            if (nextIndex in periods.indices) {
                return periods[nextIndex]
            }
        }
        return null
    }

    fun closeFolderExplorer() {
        isFolderExplorerOpen.value = false
    }

    fun clearDateFilter() {
        activeDateFilter.value = null
        isFolderExplorerOpen.value = false
    }

    fun toggleFolderExplorer() {
        if (activeDateFilter.value != null) {
            activeDateFilter.value = null
            isFolderExplorerOpen.value = true
        } else if (isFolderExplorerOpen.value) {
            isFolderExplorerOpen.value = false
        } else {
            isFolderExplorerOpen.value = true
        }
    }

    fun toggleFilter(filter: Filter) = viewModelScope.launch {
        val newFilters = activeFilters.value.toMutableSet()
        if (activeFilters.value.contains(filter.application.pkg)) {
            newFilters.remove(filter.application.pkg)
        } else {
            newFilters.add(filter.application.pkg)
        }
        activeFilters.emit(newFilters)
        filters.emit(
            applications.map {
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
