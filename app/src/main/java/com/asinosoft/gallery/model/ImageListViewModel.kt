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
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class ImageListViewModel @Inject constructor(
    state: SavedStateHandle,
    albumDao: AlbumDao,
    mediaDao: MediaDao,
    private val mediaService: MediaService,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    val albumId: Long? = state["albumId"]

    val album = MutableStateFlow<Album?>(null)

    val categories = albumDao.getAlbumCategories().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Lazily,
        initialValue = emptyList()
    )

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

    fun share(mediaIds: Collection<Long>) = viewModelScope.launchAndCatch {
        mediaService.share(mediaIds, context)
    }

    fun delete(mediaIds: Collection<Long>, callback: () -> Unit) = viewModelScope.launchAndCatch {
        mediaService.delete(mediaIds, context, callback)
    }

    fun addToAlbum(mediaIds: Collection<Long>, albumId: Long) = viewModelScope.launchAndCatch {
        mediaService.addToAlbum(mediaIds, albumId)
    }

    fun addToNewAlbum(mediaIds: Collection<Long>, name: String, category: AlbumCategory) = viewModelScope.launchAndCatch {
        mediaService.addToNewAlbum(mediaIds, name, category)
    }

    fun removeFromAlbum(mediaIds: Collection<Long>, albumId: Long) = viewModelScope.launchAndCatch {
        mediaService.removeFromAlbum(mediaIds, albumId)
    }
}
