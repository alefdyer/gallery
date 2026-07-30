package com.asinosoft.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import coil3.Image
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

data class PreloadProgress(
    val isPreloading: Boolean = false,
    val current: Int = 0,
    val total: Int = 0
) {
    val progress: Float
        get() = if (total > 0) current.toFloat() / total else 0f
}

object ThumbnailCache {
    private val _progress = MutableStateFlow(PreloadProgress())
    val progress: StateFlow<PreloadProgress> = _progress.asStateFlow()

    fun getFile(context: Context, mediaId: Long): File {
        val dir = File(context.cacheDir, "thumbnails").apply { if (!exists()) mkdirs() }
        return File(dir, "thumb_$mediaId.jpg")
    }

    suspend fun save(context: Context, mediaId: Long, image: Image) = withContext(Dispatchers.IO) {
        try {
            val file = getFile(context, mediaId)
            if (!file.exists()) {
                val bitmap = image.toBitmap()
                FileOutputStream(file).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
            }
        } catch (_: Throwable) {
        }
    }

    suspend fun preload(context: Context, mediaId: Long, uri: Uri?) = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext
        val file = getFile(context, mediaId)
        if (file.exists() && file.length() > 0) return@withContext

        try {
            val imageLoader = SingletonImageLoader.get(context)
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(getThumbnailSize(context))
                .memoryCacheKey("media-$mediaId")
                .diskCacheKey("media-$mediaId")
                .allowHardware(false)
                .build()

            val result = imageLoader.execute(request)
            result.image?.let { save(context, mediaId, it) }
        } catch (_: Throwable) {
        }
    }

    suspend fun preloadBatch(context: Context, items: List<Pair<Long, Uri?>>) =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext

            _progress.update { curr ->
                PreloadProgress(
                    isPreloading = true,
                    current = curr.current,
                    total = curr.total + items.size
                )
            }

            var processed = 0
            try {
                items.forEach { (mediaId, uri) ->
                    preload(context, mediaId, uri)
                    processed++
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
            } finally {
                withContext(NonCancellable) {
                    val remaining = items.size - processed
                    if (remaining > 0) {
                        _progress.update { curr ->
                            val updatedCurrent = curr.current + remaining
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

    private var thumbnailSize: Size? = null

    private fun getThumbnailSize(context: Context): Size {
        return thumbnailSize ?: Size(
            context.resources.displayMetrics.widthPixels / 3,
            context.resources.displayMetrics.widthPixels / 3
        ).also { thumbnailSize = it }
    }
}
