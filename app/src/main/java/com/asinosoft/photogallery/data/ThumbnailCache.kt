package com.asinosoft.photogallery.data

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
import java.security.MessageDigest
import kotlin.math.roundToInt

data class PreloadProgress(
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

private val SIZE_SUFFIX = Regex("""_(\d+)x(\d+)\.jpg$""")

object ThumbnailCache {
    private val _progress = MutableStateFlow(PreloadProgress())
    val progress: StateFlow<PreloadProgress> = _progress.asStateFlow()

    private fun getDir(context: Context): File {
        return File(context.cacheDir, "thumbnails").apply { if (!exists()) mkdirs() }
    }

    fun memoryKey(media: Media): String = "media-${identityStem(media)}"

    fun getFile(context: Context, media: Media): File {
        return findExisting(context, media) ?: writeTarget(context, media)
    }

    fun hasFile(context: Context, media: Media): Boolean {
        val file = findExisting(context, media)
        return file != null && file.length() > 0
    }

    private fun writeTarget(context: Context, media: Media): File {
        return File(getDir(context), identityFileName(media) ?: legacyFileName(media.id))
    }

    private fun findExisting(context: Context, media: Media): File? {
        val dir = getDir(context)
        identityFileName(media)?.let { name ->
            File(dir, name).takeIf { it.exists() && it.length() > 0 }?.let { return it }
        }
        matchByFilename(dir.list(), media)?.let { name ->
            File(dir, name).takeIf { it.exists() && it.length() > 0 }?.let { return it }
        }
        if (media.id > 0) {
            File(dir, legacyFileName(media.id)).takeIf { it.exists() && it.length() > 0 }?.let { return it }
        }
        return null
    }

    private fun getExistingFiles(context: Context): Set<String> {
        return getDir(context).list()?.toSet() ?: emptySet()
    }

    private fun alreadyCached(existingFiles: Set<String>, media: Media): Boolean {
        identityFileName(media)?.let { if (it in existingFiles) return true }
        if (matchByFilename(existingFiles, media) != null) return true
        return media.id > 0 && legacyFileName(media.id) in existingFiles
    }

    suspend fun save(context: Context, media: Media, image: Image) = withContext(Dispatchers.IO) {
        try {
            val file = writeTarget(context, media)
            if (!file.exists()) {
                val bitmap = image.toBitmap()
                saveBitmap(file, bitmap)
            }
        } catch (_: Throwable) {
        }
    }

    fun saveBitmap(context: Context, media: Media, bitmap: Bitmap) {
        val file = writeTarget(context, media)
        if (file.exists() && file.length() > 0) return
        saveBitmap(file, bitmap)
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
        media: Media,
        existingFiles: Set<String>? = null,
    ) = withContext(Dispatchers.IO) {
        val uri = media.uri ?: return@withContext
        if (existingFiles != null && alreadyCached(existingFiles, media)) return@withContext
        if (hasFile(context, media)) return@withContext

        val file = writeTarget(context, media)
        val isVideo = media.video != null
        try {
            val scheme = uri.scheme
            val isRemote = scheme == "http" || scheme == "https"
            // Удалённые видео не прелоадим: скачивание кадра с WebDAV/облака
            // для файлов на десятки ГБ намертво блокирует скан.
            if (isRemote && isVideo) return@withContext

            val isContent = scheme == "content"
            val video = isContent && (media.video != null
                || (true == context.contentResolver.getType(uri)?.startsWith("video/")))
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
                    .diskCacheKey(memoryKey(media))
                    .allowHardware(false)
                    .build()

                val result = imageLoader.execute(request)
                result.image?.let { save(context, media, it) }
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

    data class Request(val media: Media)

    fun startPreload(total: Int) {
        _progress.value = PreloadProgress(total = total)
    }

    fun finishPreload() {
        _progress.update { it.copy(current = it.total) }
    }

    suspend fun preloadBatch(
        context: Context,
        items: List<Request>
    ) = withContext(Dispatchers.IO) {
        val existingFiles = getExistingFiles(context)
        val missing = items
            .filter { it.media.uri != null && !alreadyCached(existingFiles, it.media) }
            .distinctBy { identityStem(it.media) }
            .sortedByDescending { it.media.video != null }

        // Добавляем прогресс по файлам, которые уже есть в кэше.
        // Если total заранее неизвестен, увеличиваем его на размер батча
        _progress.update { curr ->
            val current = curr.current + (items.size - missing.size)
            curr.copy(
                current = current,
                total = curr.total.coerceAtLeast(current + missing.size)
            )
        }

        if (missing.isEmpty()) return@withContext

        val imageSemaphore = Semaphore(IMAGE_PARALLELISM)
        val videoSemaphore = Semaphore(VIDEO_PARALLELISM)
        coroutineScope {
            missing.forEach { (media) ->
                launch {
                    val isVideo = media.video != null
                    (if (isVideo) videoSemaphore else imageSemaphore).withPermit {
                        try {
                            preload(context, media, existingFiles)
                        } finally {
                            withContext(NonCancellable) {
                                _progress.update { it.copy(current = it.current + 1) }
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

internal fun thumbBasename(filename: String, path: String = ""): String? {
    val raw = filename.ifBlank { path }.substringAfterLast('/').substringAfterLast('\\').ifEmpty { null }
    return raw?.md5()
}

internal fun identityFileName(filename: String, path: String, width: Int, height: Int): String? {
    if (width <= 0 || height <= 0) return null
    val base = thumbBasename(filename, path) ?: return null
    return "thumb_${base}_${width}x${height}.jpg"
}

internal fun identityFileName(media: Media): String? {
    val width = media.image?.width ?: 0
    val height = media.image?.height ?: 0
    return identityFileName(media.filename, media.path, width, height)
}

internal fun identityStem(media: Media): String =
    identityFileName(media)?.removePrefix("thumb_")?.removeSuffix(".jpg")
        ?: "id-${media.id}"

internal fun legacyFileName(mediaId: Long) = "thumb_$mediaId.jpg"

internal fun matchByFilename(names: Array<String>?, media: Media): String? =
    matchByFilename(names?.asIterable(), media)

internal fun matchByFilename(names: Iterable<String>?, media: Media): String? {
    if (names == null) return null
    val base = thumbBasename(media.filename, media.path) ?: return null
    val prefix = "thumb_${base}_"
    val width = media.image?.width ?: 0
    val height = media.image?.height ?: 0
    val matches = names.filter { it.startsWith(prefix) }
    if (width > 0 && height > 0) {
        return matches.firstOrNull { it == "thumb_${base}_${width}x${height}.jpg" }
    }
    return matches.singleOrNull()
}

private fun String.md5(): String {
    val md = MessageDigest.getInstance("MD5")
    val digest = md.digest(this.toByteArray(Charsets.UTF_8))
    return digest.toHexString()
}
