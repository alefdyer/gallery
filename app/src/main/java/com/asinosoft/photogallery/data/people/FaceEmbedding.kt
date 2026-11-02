package com.asinosoft.photogallery.data.people

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Rect
import com.google.mlkit.vision.face.Face
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Эмбеддинг по внешности кропа лица (не по геометрии landmarks).
 * Landmarks у всех людей похожи → давали дубликаты «одного и того же» в разных Person.
 */
object FaceEmbedding {
    private const val PATCH_SIZE = 16
    private const val PATCH_DIM = PATCH_SIZE * PATCH_SIZE
    const val DIM = PATCH_DIM

    fun encode(face: Face, fullBitmap: Bitmap): FloatArray =
        encode(fullBitmap, face.boundingBox)

    fun encode(fullBitmap: Bitmap, box: Rect): FloatArray {
        val square = expandSquare(box, fullBitmap.width, fullBitmap.height, 0.35f)
        return patchFingerprint(fullBitmap, square).also { normalizeInPlace(it) }
    }

    fun toBytes(embedding: FloatArray): ByteArray {
        val buf = ByteBuffer.allocate(embedding.size * 4).order(ByteOrder.LITTLE_ENDIAN)
        embedding.forEach { buf.putFloat(it) }
        return buf.array()
    }

    fun fromBytes(bytes: ByteArray): FloatArray {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(bytes.size / 4) { buf.float }
    }

    fun cosineSimilarity(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        if (n == 0) return 0f
        var dot = 0f
        for (i in 0 until n) dot += a[i] * b[i]
        return dot
    }

    /** Квадратный кроп вокруг bbox с запасом (плечи/причёска как на макете). */
    fun expandSquare(box: Rect, bitmapW: Int, bitmapH: Int, padFraction: Float): Rect {
        val side = maxOf(box.width(), box.height()).coerceAtLeast(1)
        val pad = (side * padFraction).toInt()
        val half = (side + pad * 2) / 2
        val cx = box.exactCenterX().toInt()
        val cy = box.exactCenterY().toInt()
        var left = cx - half
        var top = cy - half
        var right = cx + half
        var bottom = cy + half
        if (left < 0) {
            right -= left
            left = 0
        }
        if (top < 0) {
            bottom -= top
            top = 0
        }
        if (right > bitmapW) {
            left -= (right - bitmapW)
            right = bitmapW
        }
        if (bottom > bitmapH) {
            top -= (bottom - bitmapH)
            bottom = bitmapH
        }
        return Rect(
            left.coerceIn(0, bitmapW - 1),
            top.coerceIn(0, bitmapH - 1),
            right.coerceIn(1, bitmapW),
            bottom.coerceIn(1, bitmapH),
        )
    }

    private fun patchFingerprint(bitmap: Bitmap, box: Rect): FloatArray {
        val left = box.left.coerceIn(0, bitmap.width - 1)
        val top = box.top.coerceIn(0, bitmap.height - 1)
        val right = box.right.coerceIn(left + 1, bitmap.width)
        val bottom = box.bottom.coerceIn(top + 1, bitmap.height)
        val w = right - left
        val h = bottom - top
        val out = FloatArray(PATCH_DIM)
        var i = 0
        for (py in 0 until PATCH_SIZE) {
            for (px in 0 until PATCH_SIZE) {
                val x = left + (px * w) / PATCH_SIZE
                val y = top + (py * h) / PATCH_SIZE
                val c = bitmap.getPixel(
                    x.coerceIn(0, bitmap.width - 1),
                    y.coerceIn(0, bitmap.height - 1),
                )
                // Яркость с лёгким акцентом на коже (R/G)
                val gray =
                    (0.45f * Color.red(c) + 0.35f * Color.green(c) + 0.20f * Color.blue(c)) / 255f
                out[i++] = gray * 2f - 1f
            }
        }
        // Центрирование патча — меньше влияния освещения кадра
        var mean = 0f
        for (v in out) mean += v
        mean /= out.size
        for (j in out.indices) out[j] -= mean
        return out
    }

    private fun normalizeInPlace(v: FloatArray) {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum)
        if (norm < 1e-6f) return
        for (i in v.indices) v[i] /= norm
    }
}
