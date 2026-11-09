package com.asinosoft.photogallery.data.editor

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Typeface
import android.media.ExifInterface
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.audio.DefaultGainProvider
import androidx.media3.common.audio.GainProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.positiveEpoch
import com.asinosoft.photogallery.data.sortDateTimeEpochMillis
import com.asinosoft.photogallery.data.storage.StorageType
import com.asinosoft.photogallery.ui.editor.AdjustParams
import com.asinosoft.photogallery.ui.editor.paintStroke
import com.asinosoft.photogallery.ui.editor.paintTextOverlay
import com.asinosoft.photogallery.ui.editor.EditState
import com.asinosoft.photogallery.ui.editor.FilterPreset
import com.asinosoft.photogallery.ui.editor.PerspectiveQuad
import com.asinosoft.photogallery.ui.editor.stickerTextSizePx
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class EditorExport @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    fun renderPhoto(source: Bitmap, state: EditState): Bitmap {
        var bmp = source.copy(Bitmap.Config.ARGB_8888, true)
        if (state.rotationDegrees % 360 != 0 || state.flipHorizontal || state.flipVertical) {
            val m = Matrix().apply {
                if (state.flipHorizontal) postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f)
                if (state.flipVertical) postScale(1f, -1f, bmp.width / 2f, bmp.height / 2f)
                postRotate(state.rotationDegrees % 360f, bmp.width / 2f, bmp.height / 2f)
            }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
        }
        state.crop?.let { crop ->
            val l = (crop.left * bmp.width).roundToInt().coerceIn(0, bmp.width - 1)
            val t = (crop.top * bmp.height).roundToInt().coerceIn(0, bmp.height - 1)
            val r = (crop.right * bmp.width).roundToInt().coerceIn(l + 1, bmp.width)
            val b = (crop.bottom * bmp.height).roundToInt().coerceIn(t + 1, bmp.height)
            bmp = Bitmap.createBitmap(bmp, l, t, r - l, b - t)
        }
        state.perspective?.let { quad ->
            bmp = applyPerspective(bmp, quad)
        }
        val matrix = colorMatrix(state.filter, state.adjust)
        if (matrix != null) {
            val out = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
                colorFilter = ColorMatrixColorFilter(matrix)
            }
            canvas.drawBitmap(bmp, 0f, 0f, paint)
            bmp = out
        }
        if (state.strokes.isNotEmpty() || state.texts.isNotEmpty() || state.stickers.isNotEmpty()) {
            val out = bmp.copy(Bitmap.Config.ARGB_8888, true)
            val canvas = Canvas(out)
            state.strokes.forEach { stroke ->
                paintStroke(canvas, stroke, out.width, out.height)
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            state.texts.forEach { paintTextOverlay(canvas, it, out.width, out.height) }
            state.stickers.forEach { s ->
                textPaint.color = 0xFFFFFFFF.toInt()
                textPaint.textSize = stickerTextSizePx(out.width.toFloat(), s.scale)
                val cx = s.x * out.width
                val cy = s.y * out.height
                val metrics = textPaint.fontMetrics
                val baseline = cy - (metrics.ascent + metrics.descent) / 2f
                canvas.save()
                canvas.rotate(s.rotation, cx, cy)
                if (s.flipHorizontal) canvas.scale(-1f, 1f, cx, cy)
                canvas.drawText(s.emoji, cx, baseline, textPaint)
                canvas.restore()
            }
            bmp = out
        }
        return bmp
    }

    /**
     * @param asNewCapture из облака / альбома хранилища: Pictures + текущие даты, не рядом с оригиналом.
     */
    fun saveJpeg(bitmap: Bitmap, source: Media, asNewCapture: Boolean = false): Uri? {
        val placed = copyPlacement(source, video = false, asNewCapture = asNewCapture)
        val temp = File(context.cacheDir, "editor_${System.currentTimeMillis()}.jpg")
        var uri: Uri? = null
        return try {
            val compressed = temp.outputStream().use {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, it)
            }
            if (!compressed) return null
            // EXIF до первой публикации: этот телефон не заполняет дату съёмки, если дописать её позже.
            writeJpegSortExif(temp, copySortMillis(source, asNewCapture))
            val dates = copyDateValues(source, asNewCapture)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, placed.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                putAll(dates)
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, placed.relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    put(MediaStore.MediaColumns.DATA, placed.absolutePath)
                }
            }
            val resolver = context.contentResolver
            val inserted = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            uri = inserted
            val copied = resolver.openOutputStream(inserted)?.use { out ->
                temp.inputStream().use { input -> input.copyTo(out) }
                true
            } == true
            if (!copied) {
                resolver.delete(inserted, null, null)
                uri = null
                return null
            }
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(inserted, publishValues(dates), null, null)
            }
            inserted
        } catch (e: Exception) {
            Log.w(TAG, "saveJpeg failed", e)
            uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            null
        } finally {
            temp.delete()
        }
    }

    /**
     * Экспорт видео: trim + громкость/mute исходной дорожки + опциональный саундтрек.
     * Возвращает Uri в MediaStore или null.
     */
    @OptIn(UnstableApi::class)
    suspend fun exportVideo(
        input: Uri,
        state: EditState,
        source: Media,
        asNewCapture: Boolean = false,
    ): Uri? =
        withContext(Dispatchers.Main) {
            val tmp = File(
                context.cacheDir,
                "editor_video_${System.currentTimeMillis()}.mp4",
            )
            try {
                val ok = transformVideo(input, state, tmp)
                if (!ok || !tmp.exists() || tmp.length() < 1024L) {
                    tmp.delete()
                    return@withContext null
                }
                withContext(Dispatchers.IO) {
                    saveMp4(tmp, source, asNewCapture)
                }
            } finally {
                tmp.delete()
            }
        }

    @OptIn(UnstableApi::class)
    private suspend fun transformVideo(
        input: Uri,
        state: EditState,
        output: File,
    ): Boolean = suspendCancellableCoroutine { cont ->
        val startMs = state.trimStartMs.coerceAtLeast(0L)
        val endMs = state.trimEndMs
        val clipDurationMs = if (endMs != null && endMs > startMs) endMs - startMs else null

        val clipping = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(startMs)
            .apply {
                if (endMs != null && endMs > startMs) {
                    setEndPositionMs(endMs)
                }
            }
            .build()

        val videoMediaItem = MediaItem.Builder()
            .setUri(input)
            .setClippingConfiguration(clipping)
            .build()

        val removeOriginalAudio = state.muted || state.originalVolume <= 0.001f
        val videoAudioProcessors = buildList {
            if (!removeOriginalAudio && state.originalVolume < 0.999f) {
                add(
                    GainProcessor(
                        DefaultGainProvider.Builder(state.originalVolume.coerceIn(0f, 1f)).build()
                    )
                )
            }
        }
        val editedVideo = EditedMediaItem.Builder(videoMediaItem)
            .setRemoveAudio(removeOriginalAudio)
            .setEffects(Effects(videoAudioProcessors, emptyList()))
            .build()

        val videoSequence = EditedMediaItemSequence.Builder(editedVideo).build()

        val composition = if (state.soundtrackUri != null) {
            val soundtrackClipping = MediaItem.ClippingConfiguration.Builder()
                .setStartPositionMs(0L)
                .apply {
                    if (clipDurationMs != null) {
                        setEndPositionMs(clipDurationMs)
                    }
                }
                .build()
            val soundtrackItem = MediaItem.Builder()
                .setUri(state.soundtrackUri)
                .setClippingConfiguration(soundtrackClipping)
                .build()
            val soundtrackProcessors = buildList {
                if (state.soundtrackVolume < 0.999f) {
                    add(
                        GainProcessor(
                            DefaultGainProvider.Builder(
                                state.soundtrackVolume.coerceIn(0f, 1f)
                            ).build()
                        )
                    )
                }
            }
            val editedSoundtrack = EditedMediaItem.Builder(soundtrackItem)
                .setRemoveVideo(true)
                .setEffects(Effects(soundtrackProcessors, emptyList()))
                .apply {
                    if (clipDurationMs != null) {
                        setDurationUs(clipDurationMs * 1000L)
                    }
                }
                .build()
            val audioSequence = EditedMediaItemSequence.Builder(editedSoundtrack)
                .setIsLooping(clipDurationMs == null)
                .build()
            Composition.Builder(videoSequence, audioSequence).build()
        } else {
            Composition.Builder(videoSequence).build()
        }

        val transformer = Transformer.Builder(context)
            .setVideoMimeType(MimeTypes.VIDEO_H264)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(
                object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        if (cont.isActive) cont.resume(true)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        Log.w(TAG, "video transform failed", exportException)
                        if (cont.isActive) cont.resume(false)
                    }
                }
            )
            .build()

        cont.invokeOnCancellation { transformer.cancel() }
        output.delete()
        transformer.start(composition, output.absolutePath)
    }

    private fun saveMp4(file: File, source: Media, asNewCapture: Boolean = false): Uri? {
        val placed = copyPlacement(source, video = true, asNewCapture = asNewCapture)
        var uri: Uri? = null
        return try {
            val dates = copyDateValues(source, asNewCapture)
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, placed.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                putAll(dates)
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, placed.relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    put(MediaStore.MediaColumns.DATA, placed.absolutePath)
                }
            }
            val resolver = context.contentResolver
            val inserted = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            uri = inserted
            val copied = resolver.openOutputStream(inserted)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
                true
            } == true
            if (!copied) {
                resolver.delete(inserted, null, null)
                uri = null
                return null
            }
            if (Build.VERSION.SDK_INT >= 29) {
                resolver.update(inserted, publishValues(dates), null, null)
            }
            inserted
        } catch (e: Exception) {
            Log.w(TAG, "saveMp4 failed", e)
            uri?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            null
        }
    }

    /**
     * Локальный оригинал: миллисекунда вперёд — копия сразу перед ним в списке.
     * Из облака / как новый снимок: сейчас — первой в общем списке.
     */
    private fun copySortMillis(source: Media, asNewCapture: Boolean = false): Long =
        if (asNewCapture || source.storageType != StorageType.LOCAL) {
            System.currentTimeMillis()
        } else {
            source.sortDateTimeEpochMillis() + 1L
        }

    /**
     * Локально: создание как у исходного, съёмка рядом с оригиналом.
     * Из хранилища / новый снимок: все даты текущие.
     */
    private fun copyDateValues(source: Media, asNewCapture: Boolean = false): ContentValues {
        val nowMs = System.currentTimeMillis()
        val asNew = asNewCapture || source.storageType != StorageType.LOCAL
        val sortMs = copySortMillis(source, asNewCapture)
        val createdMs = if (asNew) {
            nowMs
        } else {
            positiveEpoch(source.createdAt)
                ?: positiveEpoch(source.takenAt)
                ?: sortMs
        }
        return ContentValues().apply {
            put(MediaStore.MediaColumns.DATE_ADDED, createdMs / 1000L)
            put(MediaStore.MediaColumns.DATE_MODIFIED, nowMs / 1000L)
            put(MediaStore.Images.Media.DATE_TAKEN, sortMs)
        }
    }

    /** Публикация вместе с датами: отдельный проход по файлу на этом телефоне обнуляет дату съёмки. */
    private fun publishValues(dates: ContentValues) = ContentValues(dates).apply {
        put(MediaStore.MediaColumns.IS_PENDING, 0)
    }

    /**
     * Дата съёмки должна уже лежать в JPEG к моменту, когда MediaStore видит файл первый раз.
     * Без OffsetTimeOriginal этот телефон читает местное время как UTC и сдвигает дату съёмки.
     */
    private fun writeJpegSortExif(file: File, sortMs: Long) {
        if (!file.exists()) return
        val zoned = Instant.ofEpochMilli(sortMs).atZone(ZoneId.systemDefault())
        val text = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss").format(zoned)
        val offset = zoned.offset.id.let { if (it == "Z") "+00:00" else it }
        val sub = (sortMs % 1000).toString().padStart(3, '0')
        runCatching {
            val exif = ExifInterface(file.absolutePath)
            exif.setAttribute(ExifInterface.TAG_DATETIME, text)
            exif.setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, text)
            exif.setAttribute(ExifInterface.TAG_DATETIME_DIGITIZED, text)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME, offset)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_ORIGINAL, offset)
            exif.setAttribute(ExifInterface.TAG_OFFSET_TIME_DIGITIZED, offset)
            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME, sub)
            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_ORIGINAL, sub)
            exif.setAttribute(ExifInterface.TAG_SUBSEC_TIME_DIGITIZED, sub)
            exif.saveAttributes()
        }.onFailure { Log.w(TAG, "stamp jpeg exif failed", it) }
    }

    private fun applyPerspective(source: Bitmap, quad: PerspectiveQuad): Bitmap {
        val w = source.width.toFloat()
        val h = source.height.toFloat()
        val src = floatArrayOf(
            quad.topLeftX * w, quad.topLeftY * h,
            quad.topRightX * w, quad.topRightY * h,
            quad.bottomRightX * w, quad.bottomRightY * h,
            quad.bottomLeftX * w, quad.bottomLeftY * h,
        )
        val dst = floatArrayOf(
            0f, 0f,
            w, 0f,
            w, h,
            0f, h,
        )
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(src, 0, dst, 0, 4)) {
            return source
        }
        val out = Bitmap.createBitmap(source.width, source.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(
            source,
            matrix,
            Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        )
        return out
    }

    companion object {
        private const val TAG = "EditorExport"

        fun colorMatrix(filter: FilterPreset, adjust: AdjustParams): ColorMatrix? {
            val base = when (filter) {
                FilterPreset.ORIGINAL -> null
                FilterPreset.MONO -> ColorMatrix().apply { setSaturation(0f) }
                FilterPreset.WARM -> ColorMatrix(
                    floatArrayOf(
                        1.1f, 0f, 0f, 0f, 10f,
                        0f, 1.05f, 0f, 0f, 5f,
                        0f, 0f, 0.9f, 0f, 0f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )

                FilterPreset.COOL -> ColorMatrix(
                    floatArrayOf(
                        0.9f, 0f, 0f, 0f, 0f,
                        0f, 1.0f, 0f, 0f, 0f,
                        0f, 0f, 1.15f, 0f, 15f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )

                FilterPreset.VIVID -> ColorMatrix().apply { setSaturation(1.4f) }
                FilterPreset.FADE -> ColorMatrix(
                    floatArrayOf(
                        0.9f, 0.05f, 0.05f, 0f, 20f,
                        0.05f, 0.9f, 0.05f, 0f, 20f,
                        0.05f, 0.05f, 0.9f, 0f, 20f,
                        0f, 0f, 0f, 1f, 0f,
                    )
                )
            }
            val b = adjust.brightness * 80f
            val c = 1f + adjust.contrast
            val t = (1f - c) / 2f * 255f
            val contrastBrightness = ColorMatrix(
                floatArrayOf(
                    c, 0f, 0f, 0f, t + b,
                    0f, c, 0f, 0f, t + b,
                    0f, 0f, c, 0f, t + b,
                    0f, 0f, 0f, 1f, 0f,
                )
            )
            val sat = ColorMatrix().apply { setSaturation(1f + adjust.saturation) }
            val result = ColorMatrix()
            var used = false
            if (base != null) {
                result.set(base)
                used = true
            }
            if (adjust.contrast != 0f || adjust.brightness != 0f) {
                if (used) result.postConcat(contrastBrightness) else result.set(contrastBrightness)
                used = true
            }
            if (adjust.saturation != 0f) {
                if (used) result.postConcat(sat) else result.set(sat)
                used = true
            }
            return if (used) result else null
        }
    }

    private data class CopyPlacement(
        val name: String,
        val relativePath: String,
        val absolutePath: String,
    )

    /**
     * Локально: имя-2, имя-3 рядом с оригиналом.
     * Из облака / новый снимок: IMG_/VID_ с текущим временем + Pictures/Movies.
     */
    private fun copyPlacement(
        source: Media,
        video: Boolean,
        asNewCapture: Boolean = false,
    ): CopyPlacement {
        val ext = if (video) ".mp4" else ".jpg"
        val asNew = asNewCapture || source.storageType != StorageType.LOCAL
        val relative = relativeFolder(source, video, asNewCapture)
        val taken = namesInFolder(relative, video)
        val name = if (asNew) {
            val prefix = if (video) "VID" else "IMG"
            var candidate = "${prefix}_${remoteCopyStamp()}$ext"
            var n = 2
            while (taken.any { it.equals(candidate, ignoreCase = true) }) {
                candidate = "${prefix}_${remoteCopyStamp()}_$n$ext"
                n++
                if (n > 100) break
            }
            candidate
        } else {
            val original = File(source.filename).name.ifBlank {
                source.filename.ifBlank { if (video) "video.mp4" else "photo.jpg" }
            }
            val dot = original.lastIndexOf('.')
            val stem = if (dot > 0) original.substring(0, dot) else original
            var n = 2
            var candidate = "$stem-$n$ext"
            while (taken.any { it.equals(candidate, ignoreCase = true) }) {
                n++
                candidate = "$stem-$n$ext"
                if (n > 10_000) {
                    candidate = "$stem-${System.currentTimeMillis()}$ext"
                    break
                }
            }
            candidate
        }
        val absolute = File(Environment.getExternalStorageDirectory(), relative + name).absolutePath
        return CopyPlacement(name, relative, absolute)
    }

    private fun remoteCopyStamp(): String {
        val zoned = Instant.ofEpochMilli(System.currentTimeMillis()).atZone(ZoneId.systemDefault())
        return DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss").format(zoned)
    }

    private fun relativeFolder(
        source: Media,
        video: Boolean,
        asNewCapture: Boolean = false,
    ): String {
        // Облако/WebDAV или сохранение из альбома хранилища → Pictures/Movies.
        if (asNewCapture || source.storageType != StorageType.LOCAL) {
            val bucket = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
            return "$bucket/"
        }
        val stored = source.path.trim().replace('\\', '/').trim('/')
        if (stored.isNotEmpty()) return "$stored/"
        val parent = File(source.filename).parentFile
        val root = Environment.getExternalStorageDirectory()
        val rel = parent?.let { runCatching { it.relativeTo(root).path }.getOrNull() }
            ?.replace('\\', '/')
            ?.trim('/')
        if (!rel.isNullOrEmpty() && rel != ".") return "$rel/"
        val bucket = if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES
        return "$bucket/"
    }

    private fun namesInFolder(relativePath: String, video: Boolean): MutableSet<String> {
        val names = mutableSetOf<String>()
        val collection = if (video) {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }
        val withSlash = if (relativePath.endsWith("/")) relativePath else "$relativePath/"
        val bare = withSlash.trimEnd('/')
        for (path in listOf(withSlash, bare)) {
            runCatching {
                context.contentResolver.query(
                    collection,
                    arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
                    "${MediaStore.MediaColumns.RELATIVE_PATH}=?",
                    arrayOf(path),
                    null,
                )?.use { cursor ->
                    val index = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        cursor.getString(index)?.let(names::add)
                    }
                }
            }
        }
        File(Environment.getExternalStorageDirectory(), withSlash).list()?.let(names::addAll)
        return names
    }
}
