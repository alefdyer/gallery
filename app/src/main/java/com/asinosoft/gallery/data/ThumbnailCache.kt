package com.asinosoft.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT
import android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION
import android.media.MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH
import android.net.Uri
import android.os.Build
import coil3.Image
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Scale
import coil3.size.Size
import coil3.size.pxOrElse
import coil3.toBitmap
import coil3.video.videoFrameMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

data class PreloadProgress(
    val isPreloading: Boolean = false,
    val current: Int = 0,
    val total: Int = 0
) {
    val progress: Float
        get() = if (total > 0) current.toFloat() / total else 0f
}

/** Миниатюра видео растягивается в просмотре на весь экран, поэтому сжимаем умеренно */
private const val JPEG_QUALITY = 90

private const val IMAGE_PARALLELISM = 20

/** Кадры видео упираются в системный медиасервис: больше параллельных роликов только отнимают время у фото */
private const val VIDEO_PARALLELISM = 4

object ThumbnailCache {
    private val _progress = MutableStateFlow(PreloadProgress())
    val progress: StateFlow<PreloadProgress> = _progress.asStateFlow()

    private fun getDir(context: Context): File {
        return File(context.cacheDir, "thumbnails").apply { if (!exists()) mkdirs() }
    }

    fun getFile(context: Context, mediaId: Long): File {
        return File(getDir(context), "thumb_$mediaId.jpg")
    }

    private fun getExistingFiles(context: Context): Set<String> {
        return getDir(context).list()?.toSet() ?: emptySet()
    }

    suspend fun save(context: Context, mediaId: Long, image: Image) = withContext(Dispatchers.IO) {
        try {
            val file = getFile(context, mediaId)
            if (!file.exists()) {
                val bitmap = image.toBitmap()
                saveBitmap(file, bitmap)
            }
        } catch (_: Throwable) {
        }
    }

    private fun saveBitmap(file: File, bitmap: Bitmap) {
        try {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
        } catch (_: Throwable) {
        }
    }

    suspend fun preload(
        context: Context,
        mediaId: Long,
        uri: Uri?,
        existingFiles: Set<String>? = null,
        isVideo: Boolean? = null
    ) = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext
        val fileName = "thumb_$mediaId.jpg"
        if (existingFiles?.contains(fileName) == true) return@withContext

        val file = getFile(context, mediaId)
        if (file.exists() && file.length() > 0) return@withContext

        try {
            val isContent = uri.scheme == "content"
            val video = isContent && (isVideo
                ?: (true == context.contentResolver.getType(uri)?.startsWith("video/")))
            val frame = if (video) loadFirstFrame(context, uri) else null
            if (frame != null) {
                saveBitmap(file, frame)
                frame.recycle()
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isContent && !video) {
                val size = android.util.Size(300, 300)
                val bitmap = context.contentResolver.loadThumbnail(uri, size, null)
                saveBitmap(file, bitmap)
            } else {
                val imageLoader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(getThumbnailSize(context))
                    .scale(Scale.FILL)
                    .videoFrameMillis(0)
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .diskCacheKey("media-$mediaId")
                    .allowHardware(false)
                    .build()

                val result = imageLoader.execute(request)
                result.image?.let { save(context, mediaId, it) }
            }
        } catch (_: Throwable) {
        }
    }

    /**
     * Кадр декодируется сразу в размере миниатюры, без полноразмерного битмапа.
     * Короткая сторона — сторона клетки, как у Coil со [Scale.FILL].
     */
    private fun loadFirstFrame(context: Context, uri: Uri): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            val width =
                retriever.extractMetadata(METADATA_KEY_VIDEO_WIDTH)?.toIntOrNull() ?: return null
            val height =
                retriever.extractMetadata(METADATA_KEY_VIDEO_HEIGHT)?.toIntOrNull() ?: return null
            val rotation =
                retriever.extractMetadata(METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull() ?: 0
            val (frameWidth, frameHeight) = if (rotation % 180 == 0) width to height else height to width
            if (frameWidth <= 0 || frameHeight <= 0) return null

            val side = getThumbnailSize(context).width.pxOrElse { frameWidth }
            val scale = minOf(1f, side.toFloat() / minOf(frameWidth, frameHeight))
            retriever.getScaledFrameAtTime(
                0,
                MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                (frameWidth * scale).roundToInt().coerceAtLeast(1),
                (frameHeight * scale).roundToInt().coerceAtLeast(1)
            )
        } catch (_: Throwable) {
            null
        } finally {
            retriever.release()
        }
    }

    data class Request(val mediaId: Long, val uri: Uri?, val isVideo: Boolean)

    suspend fun preloadBatch(context: Context, items: List<Request>) =
        withContext(Dispatchers.IO) {
            val existingFiles = getExistingFiles(context)
            val missing = items
                .filter { it.uri != null && "thumb_${it.mediaId}.jpg" !in existingFiles }
                .distinctBy { it.mediaId }
                .sortedByDescending { it.isVideo }
            if (missing.isEmpty()) return@withContext

            _progress.update { curr ->
                if (curr.isPreloading) curr.copy(total = curr.total + missing.size)
                else PreloadProgress(isPreloading = true, current = 0, total = missing.size)
            }

            val imageSemaphore = Semaphore(IMAGE_PARALLELISM)
            val videoSemaphore = Semaphore(VIDEO_PARALLELISM)
            coroutineScope {
                missing.forEach { (mediaId, uri, isVideo) ->
                    launch {
                        (if (isVideo) videoSemaphore else imageSemaphore).withPermit {
                            try {
                                preload(context, mediaId, uri, existingFiles, isVideo)
                            } finally {
                                withContext(NonCancellable) {
                                    _progress.update { curr ->
                                        val updatedCurrent = curr.current + 1
                                        val isDone = updatedCurrent >= curr.total
                                        PreloadProgress(
                                            isPreloading = !isDone,
                                            current = updatedCurrent,
                                            total = curr.total
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

    private var thumbnailSize: Size? = null

    private fun getThumbnailSize(context: Context): Size {
        return thumbnailSize ?: Size(
            context.resources.displayMetrics.widthPixels / 3,
            context.resources.displayMetrics.widthPixels / 3
        ).also { thumbnailSize = it }
    }
}
