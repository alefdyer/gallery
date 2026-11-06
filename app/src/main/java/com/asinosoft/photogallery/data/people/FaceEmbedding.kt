package com.asinosoft.photogallery.data.people

import android.graphics.Rect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.round
import kotlin.math.sqrt

/** Утилиты для 192-d MobileFaceNet-векторов (см. [FaceEmbedder]). */
object FaceEmbedding {
    const val DIM = FaceEmbedder.DIM

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

    /** Евклидово расстояние (как в исходном MobileFaceNet demo). Меньше = похожее. */
    fun euclideanDistance(a: FloatArray, b: FloatArray): Float {
        val n = minOf(a.size, b.size)
        if (n == 0) return Float.MAX_VALUE
        var sum = 0f
        for (i in 0 until n) {
            val d = a[i] - b[i]
            sum += d * d
        }
        return sqrt(sum)
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

    fun normalizeInPlace(v: FloatArray) {
        var sum = 0f
        for (x in v) sum += x * x
        val norm = sqrt(sum)
        if (norm < 1e-6f) return
        for (i in v.indices) v[i] /= norm
    }

    /**
     * L2-нормированное среднее ближайших [fraction] векторов к [reference].
     * Пограничные в среднее не входят. Хотя бы один вектор остаётся.
     */
    fun coreAverage(
        vectors: List<FloatArray>,
        reference: FloatArray,
        fraction: Float = 0.7f,
    ): FloatArray {
        require(vectors.isNotEmpty())
        val ref = reference.copyOf()
        normalizeInPlace(ref)
        val keep = round(vectors.size * fraction.toDouble()).toInt().coerceIn(1, vectors.size)
        val closest = vectors.sortedBy { euclideanDistance(it, ref) }.take(keep)
        val dim = closest.first().size
        val avg = FloatArray(dim)
        for (v in closest) {
            val n = minOf(dim, v.size)
            for (i in 0 until n) avg[i] += v[i]
        }
        val count = closest.size.toFloat()
        for (i in avg.indices) avg[i] /= count
        normalizeInPlace(avg)
        return avg
    }
}
