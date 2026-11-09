package com.asinosoft.photogallery.data

import android.content.Context
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.File
import okhttp3.OkHttpClient
import okhttp3.Request

data class ExifProbe(
    val takenAt: Long? = null,
    val width: Int = 0,
    val height: Int = 0,
    val orientation: Int = 0,
)

/**
 * EXIF / bounds из начала файла (Range): съёмка + разрешение.
 */
object ExifTakenAt {
    private const val TAG = "ExifTakenAt"
    private const val HEAD_BYTES = 128 * 1024L

    fun fromHttp(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): Long? = probeHttp(client, url, headers).takenAt

    fun probeHttp(
        client: OkHttpClient,
        url: String,
        headers: Map<String, String> = emptyMap(),
    ): ExifProbe {
        if (!url.startsWith("http://") && !url.startsWith("https://")) return ExifProbe()
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-${HEAD_BYTES - 1}")
            .get()
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful && response.code != 206) {
                    Log.w(TAG, "HTTP ${response.code} $url")
                    return ExifProbe()
                }
                val bytes = response.body?.bytes() ?: return ExifProbe()
                fromBytes(bytes)
            }
        } catch (ex: Throwable) {
            Log.w(TAG, "EXIF failed $url", ex)
            ExifProbe()
        }
    }

    fun fromBytes(bytes: ByteArray): ExifProbe {
        if (bytes.isEmpty()) return ExifProbe()
        return try {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            var width = bounds.outWidth.coerceAtLeast(0)
            var height = bounds.outHeight.coerceAtLeast(0)

            val exif = ExifInterface(ByteArrayInputStream(bytes))
            fromBoundsAndExif(width, height, exif)
        } catch (ex: Throwable) {
            Log.w(TAG, "parse EXIF failed", ex)
            ExifProbe()
        }
    }

    /** Размер как на экране (с учётом EXIF), для локального файла / content URI. */
    fun probeUri(context: Context, uri: Uri): ExifProbe = try {
        val path = uri.path?.takeIf {
            uri.scheme == "file" && !it.isNullOrBlank() && File(it).isFile
        }
        if (path != null) {
            probeFile(File(path))
        } else {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeStream(input, null, bounds)
                var width = bounds.outWidth.coerceAtLeast(0)
                var height = bounds.outHeight.coerceAtLeast(0)
                // Второй stream: decodeStream съедает первый.
                context.contentResolver.openInputStream(uri)?.use { exifStream ->
                    fromBoundsAndExif(width, height, ExifInterface(exifStream))
                } ?: ExifProbe(width = width, height = height)
            } ?: ExifProbe()
        }
    } catch (ex: Throwable) {
        Log.w(TAG, "probeUri failed $uri", ex)
        ExifProbe()
    }

    fun probeFile(file: File): ExifProbe = try {
        if (!file.isFile) return ExifProbe()
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val width = bounds.outWidth.coerceAtLeast(0)
        val height = bounds.outHeight.coerceAtLeast(0)
        fromBoundsAndExif(width, height, ExifInterface(file.absolutePath))
    } catch (ex: Throwable) {
        Log.w(TAG, "probeFile failed ${file.path}", ex)
        ExifProbe()
    }

    private fun fromBoundsAndExif(
        rawWidth: Int,
        rawHeight: Int,
        exif: ExifInterface,
    ): ExifProbe {
        var width = rawWidth
        var height = rawHeight
        val raw = exif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME_DIGITIZED)
            ?: exif.getAttribute(ExifInterface.TAG_DATETIME)
        val takenAt = parseMediaDateMillis(raw)
        val orientation = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, 0)
        if (width <= 0) {
            width = exif.getAttributeInt(ExifInterface.TAG_IMAGE_WIDTH, 0)
        }
        if (height <= 0) {
            height = exif.getAttributeInt(ExifInterface.TAG_IMAGE_LENGTH, 0)
        }
        // EXIF orientation 6/8 — на экране стороны меняются местами (как у Coil).
        val (displayW, displayH) = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90,
            ExifInterface.ORIENTATION_ROTATE_270,
            ExifInterface.ORIENTATION_TRANSVERSE,
            ExifInterface.ORIENTATION_TRANSPOSE,
                -> height to width

            else -> width to height
        }
        return ExifProbe(
            takenAt = takenAt,
            width = displayW,
            height = displayH,
            orientation = orientation,
        )
    }
}
