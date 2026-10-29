package com.asinosoft.photogallery.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.util.Log
import com.asinosoft.photogallery.GalleryApp
import com.asinosoft.photogallery.data.storage.StorageProviderRegistry
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import okhttp3.Request
import kotlin.math.roundToInt

/**
 * Фоновая очередь первого кадра для remote-видео без native preview.
 * Не больше одного извлечения одновременно; без HTTP Range — пропуск.
 */
@Singleton
class RemoteVideoThumbQueue @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storageProviderRegistry: StorageProviderRegistry,
) {
    data class Job(
        val mediaId: Long,
        val storageId: Long,
        val storageItemId: String,
        val size: Long,
        val uri: String? = null
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<Job>(Channel.UNLIMITED)
    private val started = AtomicBoolean(false)

    fun enqueue(job: Job) {
        ensureStarted()
        channel.trySend(job)
    }

    private fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            for (job in channel) {
                runCatching { process(job) }
                    .onFailure { Log.w(TAG, "thumb job failed ${job.mediaId}", it) }
            }
        }
    }

    private suspend fun process(job: Job) {
        val file = ThumbnailCache.getFile(context, job.mediaId)
        if (file.exists() && file.length() > 0) return
        if (job.size > MAX_SIZE_BYTES) return

        val url = job.uri ?: run {
            val provider = storageProviderRegistry.getStorageProvider(job.storageId)
            runCatching {
                provider.getMediaUri(
                    Media(
                        id = job.mediaId,
                        storageId = job.storageId,
                        storageItemId = job.storageItemId,
                        size = job.size,
                        video = Video(duration = 0)
                    )
                ).toString()
            }.getOrNull()
        } ?: return

        if (!url.startsWith("http://") && !url.startsWith("https://")) return
        if (!supportsRange(url)) return

        val bitmap = extractFrame(url) ?: return
        ThumbnailCache.saveBitmap(context, job.mediaId, bitmap)
        bitmap.recycle()
    }

    private fun supportsRange(url: String): Boolean {
        val app = context.applicationContext as? GalleryApp ?: return false
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .head()
            .build()
        return try {
            app.httpClient.newCall(request).execute().use { response ->
                response.code == 206 ||
                        response.header("Accept-Ranges")
                            ?.contains("bytes", ignoreCase = true) == true ||
                        // Некоторые серверы отвечают 200 на HEAD с Range, но отдают Content-Range.
                        response.header("Content-Range") != null
            }
        } catch (_: Throwable) {
            false
        }
    }

    private fun extractFrame(url: String): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(url, HashMap())
            val width = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                ?.toIntOrNull() ?: return null
            val height = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                ?.toIntOrNull() ?: return null
            if (width <= 0 || height <= 0) return null
            val side = (context.resources.displayMetrics.widthPixels / 3).coerceAtLeast(128)
            val scale = minOf(1f, side.toFloat() / minOf(width, height))
            retriever.getScaledFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                (width * scale).roundToInt().coerceAtLeast(1),
                (height * scale).roundToInt().coerceAtLeast(1)
            )
        } catch (_: Throwable) {
            null
        } finally {
            retriever.release()
        }
    }

    companion object {
        private const val TAG = "RemoteVideoThumb"
        private const val MAX_SIZE_BYTES = 2L * 1024 * 1024 * 1024 // 2 GiB
    }
}
