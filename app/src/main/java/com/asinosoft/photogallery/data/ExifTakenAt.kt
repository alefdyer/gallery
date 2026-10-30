package com.asinosoft.photogallery.data

import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.util.Log
import java.io.ByteArrayInputStream
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
            // EXIF orientation 6/8 — на экране стороны меняются местами.
            val (displayW, displayH) = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90,
                ExifInterface.ORIENTATION_ROTATE_270,
                ExifInterface.ORIENTATION_TRANSVERSE,
                ExifInterface.ORIENTATION_TRANSPOSE,
                    -> height to width

                else -> width to height
            }
            ExifProbe(
                takenAt = takenAt,
                width = displayW,
                height = displayH,
                orientation = orientation,
            )
        } catch (ex: Throwable) {
            Log.w(TAG, "parse EXIF failed", ex)
            ExifProbe()
        }
    }
}
