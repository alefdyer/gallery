package com.asinosoft.gallery.model

import android.content.Context
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.MediaDao
import com.asinosoft.gallery.data.MediaService
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
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
    private val albumId = MutableStateFlow<UUID?>(null)
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

    fun setAlbumId(id: UUID) = viewModelScope.launch {
        albumId.emit(id)
    }

    fun delete(
        media: Collection<Media>,
        context: Context,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) = viewModelScope.launch {
        try {
            service.delete(media, context, launcher)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun postDelete(media: Collection<Media>) = viewModelScope.launch {
        try {
            service.postDelete(media)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun edit(media: Media, context: Context) = viewModelScope.launch {
        try {
            service.edit(media, context)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun share(media: Collection<Media>, context: Context) = viewModelScope.launch {
        try {
            service.share(media, context)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun addToAlbum(media: Collection<Media>, album: Album) = viewModelScope.launch {
        try {
            service.addToAlbum(media, album)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun addToNewAlbum(media: Collection<Media>, name: String) = viewModelScope.launch {
        try {
            val id = service.createAlbum(name)
            service.addToAlbum(media, id)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun removeFromAlbum(media: Collection<Media>, album: Album) = viewModelScope.launch {
        service.removeFromAlbum(media, album)
    }
}
