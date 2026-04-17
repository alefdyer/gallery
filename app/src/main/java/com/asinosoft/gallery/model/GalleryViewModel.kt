package com.asinosoft.gallery.model

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.MediaDao
import com.asinosoft.gallery.data.MediaService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val service: MediaService,
    private val albumDao: AlbumDao,
    mediaDao: MediaDao
) : ViewModel() {
    private val albumId = MutableStateFlow<Long?>(null)
    private val rescanFlow = MutableStateFlow(false)
    private val messageFlow = MutableStateFlow<String?>(null)

    val albums = albumDao.getAlbums()

    val images = mediaDao.getImages()

    val isRescanning: StateFlow<Boolean> = rescanFlow

    val message: StateFlow<String?> = messageFlow

    @OptIn(ExperimentalCoroutinesApi::class)
    val albumImages = albumId.filterNotNull().flatMapLatest { albumDao.getMediaInAlbum(it) }

    suspend fun clearMessage() {
        messageFlow.emit(null)
    }

    fun rescan() = viewModelScope.launch {
        rescanFlow.emit(true)
        try {
            service.updateAll()
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
        rescanFlow.emit(false)
    }

    fun setAlbumId(id: Long) = viewModelScope.launch {
        albumId.emit(id)
    }

    fun delete(
        mediaIds: Collection<Long>,
        context: Context,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) = viewModelScope.launch {
        try {
            service.delete(mediaIds, context, launcher)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun postDelete(mediaIds: Collection<Long>) = viewModelScope.launch {
        try {
            service.postDelete(mediaIds)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun edit(mediaId: Long, context: Context) = viewModelScope.launch {
        try {
            service.edit(mediaId, context)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun share(mediaIds: Collection<Long>, context: Context) = viewModelScope.launch {
        try {
            service.share(mediaIds, context)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun addToAlbum(mediaIds: Collection<Long>, albumId: Long) = viewModelScope.launch {
        try {
            service.addToAlbum(mediaIds, albumId)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun addToNewAlbum(mediaIds: Collection<Long>, name: String) = viewModelScope.launch {
        try {
            val album = service.createAlbum(name)
            service.addToAlbum(mediaIds, album.id)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun removeFromAlbum(mediaIds: Collection<Long>, albumId: Long) = viewModelScope.launch {
        service.removeFromAlbum(mediaIds, albumId)
    }
}
