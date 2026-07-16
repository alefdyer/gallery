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

    @OptIn(ExperimentalCoroutinesApi::class)
    var filters = MutableStateFlow<List<Filter>>(listOf())

    val filteredImages = images.combine(activeFilters) { images, filters ->
        if (filters.isEmpty()) images
        else images.filter { filters.contains(it.owner) }
    }

    init {
        viewModelScope.launch {
            albumId?.let { albumId ->
                val value = albumDao.getAlbumById(albumId)
                album.emit(value)
            }


            images.collect { images ->
                val owners = images.mapNotNull { it.owner }.toSet()
                applications = applicationDao.getApplications(owners).sortedBy { it.name }
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
