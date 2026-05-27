package com.asinosoft.gallery.model

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.MediaDao
import com.asinosoft.gallery.data.MediaService
import com.asinosoft.gallery.data.launchAndCatch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

@HiltViewModel
class ImageListViewModel @Inject constructor(
    state: SavedStateHandle,
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
    private val mediaService: MediaService,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    val albumId: Long? = state["albumId"]

    val images: Flow<PagingData<Media>>
        get() = Pager(
            config = PagingConfig(pageSize = 20),
            pagingSourceFactory = {
                albumId?.let { albumDao.getMediaInAlbum(albumId) }
                    ?: mediaDao.getImages()
            }
        ).flow.cachedIn(viewModelScope)

    fun share(mediaIds: Collection<Long>) = viewModelScope.launchAndCatch {
        mediaService.share(mediaIds, context)
    }

    fun delete(mediaIds: Collection<Long>, callback: () -> Unit) = viewModelScope.launchAndCatch {
        mediaService.delete(mediaIds, context, callback)
    }

    fun addToAlbum(mediaIds: Collection<Long>, albumId: Long) = viewModelScope.launchAndCatch {
        mediaService.addToAlbum(mediaIds, albumId)
    }

    fun addToNewAlbum(mediaIds: Collection<Long>, name: String) = viewModelScope.launchAndCatch {
        val album = mediaService.createAlbum(name)
        mediaService.addToAlbum(mediaIds, album.id)
    }

    fun removeFromAlbum(mediaIds: Collection<Long>, albumId: Long) = viewModelScope.launchAndCatch {
        mediaService.removeFromAlbum(mediaIds, albumId)
    }
}
