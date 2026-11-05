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
import com.asinosoft.photogallery.ui.editor.AdjustParams
import com.asinosoft.photogallery.ui.editor.EditState
import com.asinosoft.photogallery.ui.editor.FilterPreset
import com.asinosoft.photogallery.ui.editor.PerspectiveQuad
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
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
        if (state.rotationDegrees % 360 != 0 || state.flipHorizontal) {
            val m = Matrix().apply {
                if (state.flipHorizontal) postScale(-1f, 1f, bmp.width / 2f, bmp.height / 2f)
                postRotate(state.rotationDegrees % 360f, bmp.width / 2f, bmp.height / 2f)
            }
            bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
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
            val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }
            state.strokes.forEach { stroke ->
                if (stroke.points.size < 2) return@forEach
                strokePaint.color = stroke.color.toInt()
                strokePaint.strokeWidth = stroke.width * out.width
                val path = android.graphics.Path()
                val first = stroke.points.first()
                path.moveTo(first.x * out.width, first.y * out.height)
                stroke.points.drop(1).forEach { p ->
                    path.lineTo(p.x * out.width, p.y * out.height)
                }
                canvas.drawPath(path, strokePaint)
            }
            val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                typeface = Typeface.DEFAULT_BOLD
                textAlign = Paint.Align.CENTER
            }
            state.texts.forEach { t ->
                textPaint.color = t.color.toInt()
                textPaint.textSize = 48f * t.scale * (out.width / 1080f)
                canvas.drawText(t.text, t.x * out.width, t.y * out.height, textPaint)
            }
            state.stickers.forEach { s ->
                textPaint.color = 0xFFFFFFFF.toInt()
                textPaint.textSize = 72f * s.scale * (out.width / 1080f)
                val cx = s.x * out.width
                val cy = s.y * out.height
                canvas.save()
                canvas.rotate(s.rotation, cx, cy)
                if (s.flipHorizontal) canvas.scale(-1f, 1f, cx, cy)
                canvas.drawText(s.emoji, cx, cy, textPaint)
                canvas.restore()
            }
            bmp = out
        }
        return bmp
    }

    fun saveJpeg(bitmap: Bitmap, source: Media): Uri? {
        val placed = copyPlacement(source, video = false)
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, placed.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, placed.relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    put(MediaStore.MediaColumns.DATA, placed.absolutePath)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 92, out)
            } ?: run {
                resolver.delete(uri, null, null)
                return null
            }
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            Log.w(TAG, "saveJpeg failed", e)
            null
        }
    }

    /**
     * Экспорт видео: trim + громкость/mute исходной дорожки + опциональный саундтрек.
     * Возвращает Uri в MediaStore или null.
     */
    @OptIn(UnstableApi::class)
    suspend fun exportVideo(input: Uri, state: EditState, source: Media): Uri? =
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
                    saveMp4(tmp, source)
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

    private fun saveMp4(file: File, source: Media): Uri? {
        val placed = copyPlacement(source, video = true)
        return try {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, placed.name)
                put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
                if (Build.VERSION.SDK_INT >= 29) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, placed.relativePath)
                    put(MediaStore.MediaColumns.IS_PENDING, 1)
                } else {
                    put(MediaStore.MediaColumns.DATA, placed.absolutePath)
                }
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
                ?: return null
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } ?: run {
                resolver.delete(uri, null, null)
                return null
            }
            if (Build.VERSION.SDK_INT >= 29) {
                values.clear()
                values.put(MediaStore.MediaColumns.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            uri
        } catch (e: Exception) {
            Log.w(TAG, "saveMp4 failed", e)
            null
        }
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

    /** Копия рядом с оригиналом: имя-2, имя-3, … первый свободный номер. */
    private fun copyPlacement(source: Media, video: Boolean): CopyPlacement {
        val original = File(source.filename).name.ifBlank {
            source.filename.ifBlank { if (video) "video.mp4" else "photo.jpg" }
        }
        val dot = original.lastIndexOf('.')
        val stem = if (dot > 0) original.substring(0, dot) else original
        val ext = if (video) ".mp4" else ".jpg"
        val relative = relativeFolder(source, video)
        val taken = namesInFolder(relative, video)
        var n = 2
        var name = "$stem-$n$ext"
        while (taken.any { it.equals(name, ignoreCase = true) }) {
            n++
            name = "$stem-$n$ext"
            if (n > 10_000) {
                name = "$stem-${System.currentTimeMillis()}$ext"
                break
            }
        }
        val absolute = File(Environment.getExternalStorageDirectory(), relative + name).absolutePath
        return CopyPlacement(name, relative, absolute)
    }

    private fun relativeFolder(source: Media, video: Boolean): String {
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
