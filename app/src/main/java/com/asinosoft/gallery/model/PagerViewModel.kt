package com.asinosoft.gallery.model

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PagerViewModel @Inject constructor(
    state: SavedStateHandle,
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
    @param:ApplicationContext private val context: Context,
    private val mediaService: MediaService
) : ViewModel() {
    private val albumId: Long? = state["albumId"]
    private val imageId: Long = state["imageId"]!!

    val images: Flow<PagingData<Media>> = Pager(
            config = PagingConfig(pageSize = 20, enablePlaceholders = false),
            pagingSourceFactory = {
                albumId?.let { albumDao.getMediaInAlbum(albumId) }
                    ?: mediaDao.getImages()
            }
        ).flow.cachedIn(viewModelScope)

    val offset = MutableStateFlow(0)

    init {
        viewModelScope.launch {
            val index = albumId?.let { albumDao.getImageIndex(albumId, imageId) }
                ?: mediaDao.getImageIndex(imageId)

            Log.i("pager", "Offset = $index")
            offset.emit(index)
        }
    }

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
