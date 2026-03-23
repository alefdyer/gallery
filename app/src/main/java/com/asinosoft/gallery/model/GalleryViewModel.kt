package com.asinosoft.gallery.model

import android.content.Context
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.Media
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
    albumDao: AlbumDao,
    private val mediaDao: MediaDao
) : ViewModel() {
    private val albumName = MutableStateFlow<String?>(null)
    private val rescanFlow = MutableStateFlow(false)
    private val messageFlow = MutableStateFlow<String?>(null)

    val albums = albumDao.getAlbums()

    val images = mediaDao.getImages()

    val isRescanning: StateFlow<Boolean> = rescanFlow

    val message: StateFlow<String?> = messageFlow

    @OptIn(ExperimentalCoroutinesApi::class)
    val albumImages = albumName.filterNotNull().flatMapLatest { mediaDao.getAlbumImages(it) }

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

    fun setAlbumName(name: String) = viewModelScope.launch {
        albumName.emit(name)
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

    fun move(
        media: Collection<Media>,
        album: String,
        context: Context,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) = viewModelScope.launch {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                service.move(media, album, context, launcher)
            } catch (ex: Throwable) {
                messageFlow.emit(ex.message)
            }
        } else {
            return@launch messageFlow.emit("Move into album is unsupported below Android 10")
        }
    }

    fun postMove(context: Context) = viewModelScope.launch {
        try {
            service.postMove(context)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }
}
