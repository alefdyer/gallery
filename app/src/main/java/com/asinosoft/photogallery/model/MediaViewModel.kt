package com.asinosoft.photogallery.model

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.photogallery.data.EXTERNAL_STORAGE_ID
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.MediaDao
import com.asinosoft.photogallery.data.Video
import com.asinosoft.photogallery.data.plausibleVideoDurationMs
import com.asinosoft.photogallery.data.storage.StorageService
import com.asinosoft.photogallery.data.storage.StorageProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

data class PlaybackSource(
    val uri: Uri,
    val headers: Map<String, String> = emptyMap(),
)

@HiltViewModel
open class MediaViewModel @Inject constructor(
    private val storageProviderRegistry: StorageProviderRegistry,
    private val storageService: StorageService,
    private val mediaDao: MediaDao,
) : ViewModel() {
    suspend fun getMediaUri(media: Media): Uri =
        if (EXTERNAL_STORAGE_ID == media.storageId) {
            requireNotNull(media.uri)
        } else {
            prepareRemoteAuth(media.storageId)
            storageProviderRegistry.getStorageProvider(media.storageId)
                .getMediaUri(media)
        }

    /** URI + auth headers (для LibVLC, у которого нет нашего OkHttp interceptor). */
    suspend fun getPlaybackSource(media: Media): PlaybackSource {
        val uri = getMediaUri(media)
        if (EXTERNAL_STORAGE_ID == media.storageId || media.storageId == LOCAL_PHONE_STORAGE_ID) {
            return PlaybackSource(uri)
        }
        return PlaybackSource(uri, storageService.httpAuthHeaders(media.storageId))
    }

    suspend fun getThumbnailUri(media: Media): Uri {
        prepareRemoteAuth(media.storageId)
        return storageProviderRegistry.getStorageProvider(media.storageId)
            .getThumbnailUri(media)
    }

    /** Сохраняет проверенную длительность (.MPG после probe / EndReached). */
    fun saveVideoDuration(mediaId: Long, durationMs: Long) {
        if (mediaId <= 0L || durationMs <= 0L) return
        viewModelScope.launch(Dispatchers.IO) {
            val media = mediaDao.getById(mediaId) ?: return@launch
            val video = media.video ?: return@launch
            val next = plausibleVideoDurationMs(media.size, durationMs) ?: durationMs
            val prev = video.duration
            if (prev == next) return@launch
            // Не затираем уже короткое правдоподобное более длинным.
            if (prev > 0L &&
                plausibleVideoDurationMs(media.size, prev) != null &&
                next > prev * 11L / 10L
            ) {
                return@launch
            }
            mediaDao.upsert(media.copy(video = Video(duration = next)))
        }
    }

    private suspend fun prepareRemoteAuth(storageId: Long) {
        // null = ждём экран Google (GoogleDriveReauthHandler); не грузим со старым Bearer.
        checkNotNull(storageService.ensureFreshAuth(storageId)) {
            "Waiting for Google sign-in"
        }
        storageProviderRegistry.invalidate(storageId)
    }

    private companion object {
        const val LOCAL_PHONE_STORAGE_ID = 1L
    }
}
