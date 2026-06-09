package com.asinosoft.gallery.model

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumCategory
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.MediaDao
import com.asinosoft.gallery.data.MediaService
import com.asinosoft.gallery.data.launchAndCatch
import com.asinosoft.gallery.data.storage.StorageDao
import com.asinosoft.gallery.data.storage.StorageService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.collections.minus
import kotlin.collections.plus

@HiltViewModel
class ImageListViewModel @Inject constructor(
    state: SavedStateHandle,
    albumDao: AlbumDao,
    mediaDao: MediaDao,
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

    init {
        viewModelScope.launch {
            albumId?.let { albumId ->
                val value = albumDao.getAlbumById(albumId)
                album.emit(value)
            }
        }
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

    fun addSelectionToNewAlbum(name: String, category: AlbumCategory) = viewModelScope.launchAndCatch {
        mediaService.addToNewAlbum(selection.value, name, category)
        clearSelection()
    }

    fun removeSelectionFromAlbum(albumId: Long) = viewModelScope.launchAndCatch {
        mediaService.removeFromAlbum(selection.value, albumId)
        clearSelection()
    }
}
