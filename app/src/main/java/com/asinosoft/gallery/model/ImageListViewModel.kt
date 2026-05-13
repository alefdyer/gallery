package com.asinosoft.gallery.model

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.MediaDao
import com.asinosoft.gallery.data.MediaService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ImageListViewModel @Inject constructor(
    state: SavedStateHandle,
    albumDao: AlbumDao,
    mediaDao: MediaDao,
    private val mediaService: MediaService,
    @param:ApplicationContext private val context: Context
): ViewModel() {
    val albumId: Long? = state["albumId"]

    val images: Flow<List<Media>> =
        if (null == albumId) {
            mediaDao.getImages()
        } else {
            albumDao.getMediaInAlbum(albumId)
        }

    fun share(mediaIds: Collection<Long>) = viewModelScope.launch {
        mediaService.share(mediaIds, context)
    }

    fun delete(mediaIds: Collection<Long>, callback: () -> Unit) = viewModelScope.launch {
        mediaService.delete(mediaIds, context, callback)
    }

    fun addToAlbum(mediaIds: Collection<Long>, albumId: Long) = viewModelScope.launch {
        mediaService.addToAlbum(mediaIds, albumId)
    }

    fun addToNewAlbum(mediaIds: Collection<Long>, name: String) = viewModelScope.launch {
        val album = mediaService.createAlbum(name)
        mediaService.addToAlbum(mediaIds, album.id)
    }

    fun removeFromAlbum(mediaIds: Collection<Long>, albumId: Long) = viewModelScope.launch {
        mediaService.removeFromAlbum(mediaIds, albumId)
    }
}
