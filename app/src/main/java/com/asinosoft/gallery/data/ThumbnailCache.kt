package com.asinosoft.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import coil3.Image
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
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
                bitmap.compress(Bitmap.CompressFormat.JPEG, 75, out)
            }
        } catch (_: Throwable) {
        }
    }

    suspend fun preload(context: Context, mediaId: Long, uri: Uri?, existingFiles: Set<String>? = null) = withContext(Dispatchers.IO) {
        if (uri == null) return@withContext
        val fileName = "thumb_$mediaId.jpg"
        if (existingFiles?.contains(fileName) == true) return@withContext

        val file = getFile(context, mediaId)
        if (file.exists() && file.length() > 0) return@withContext

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.scheme == "content") {
                val size = android.util.Size(300, 300)
                val bitmap = context.contentResolver.loadThumbnail(uri, size, null)
                saveBitmap(file, bitmap)
            } else {
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
            }
        } catch (_: Throwable) {
        }
    }

    suspend fun preloadBatch(context: Context, items: List<Pair<Long, Uri?>>) =
        withContext(Dispatchers.IO) {
            if (items.isEmpty()) return@withContext

            val existingFiles = getExistingFiles(context)

            _progress.update { curr ->
                PreloadProgress(
                    isPreloading = true,
                    current = curr.current,
                    total = curr.total + items.size
                )
            }

            val semaphore = Semaphore(20)
            coroutineScope {
                items.forEach { (mediaId, uri) ->
                    launch {
                        semaphore.withPermit {
                            try {
                                preload(context, mediaId, uri, existingFiles)
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
