package com.asinosoft.gallery.model

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.MediaDao
import com.asinosoft.gallery.data.MediaService
import com.asinosoft.gallery.data.launchAndCatch
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@HiltViewModel
class PagerViewModel @Inject constructor(
    state: SavedStateHandle,
    albumDao: AlbumDao,
    mediaDao: MediaDao,
    @param:ApplicationContext private val context: Context,
    private val mediaService: MediaService
) : ViewModel() {
    private val albumId: Long? = state["albumId"]
    private val imageId: Long = state["imageId"]!!

    val images: Flow<List<Media>> =
        if (null == albumId) {
            mediaDao.getImages()
        } else {
            albumDao.getMediaInAlbum(albumId)
        }

    val offset: Flow<Int> = images.map { it.indexOfFirst { image -> image.id == imageId } }

    fun delete(media: Media, callback: () -> Unit) = viewModelScope.launchAndCatch {
        mediaService.delete(setOf(media.id), context, callback)
    }

    fun edit(media: Media) = viewModelScope.launchAndCatch {
        mediaService.edit(media.id, context)
    }

    fun share(media: Media) = viewModelScope.launchAndCatch {
        mediaService.share(setOf(media.id), context)
    }
}
