package com.asinosoft.photogallery.data.people

import kotlin.math.sqrt

/**
 * Выравнивание лица в шаблон 112×112 (глаза, нос, углы рта).
 * Similarity: масштаб, поворот и сдвиг, без отражения.
 * Глаза и углы рта названы со стороны человека: его левый глаз стоит справа на кадре.
 */
object FaceAlign {
    data class Point(val x: Float, val y: Float)

    data class Similarity(
        val a: Float,
        val b: Float,
        val tx: Float,
        val ty: Float,
    ) {
        fun apply(x: Float, y: Float): Point =
            Point(a * x - b * y + tx, b * x + a * y + ty)
    }

    /** Точки в пикселях полного кадра. */
    data class Landmarks(
        val rightEye: Point?,
        val leftEye: Point?,
        val nose: Point? = null,
        val mouthRight: Point? = null,
        val mouthLeft: Point? = null,
    )

    val templateRightEye = Point(38.2946f, 51.6963f)
    val templateLeftEye = Point(73.5318f, 51.5014f)
    val templateNose = Point(56.0252f, 71.7366f)
    val templateMouthRight = Point(41.5493f, 92.3655f)
    val templateMouthLeft = Point(70.7299f, 92.2041f)

    /** Оба глаза обязательны. Нет пары или точки слиплись — null, кроп остаётся квадратным. */
    fun similarityForFace(landmarks: Landmarks): Similarity? {
        val right = landmarks.rightEye ?: return null
        val left = landmarks.leftEye ?: return null
        if (distance(right, left) < MIN_EYE_DISTANCE) return null
        val src = ArrayList<Point>(5)
        val dst = ArrayList<Point>(5)
        src += right
        dst += templateRightEye
        src += left
        dst += templateLeftEye
        landmarks.nose?.let {
            src += it
            dst += templateNose
        }
        landmarks.mouthRight?.let {
            src += it
            dst += templateMouthRight
        }
        landmarks.mouthLeft?.let {
            src += it
            dst += templateMouthLeft
        }
        return similarity(src, dst)
    }

    fun similarity(src: List<Point>, dst: List<Point>): Similarity? {
        if (src.size != dst.size || src.size < 2) return null
        var srcX = 0f
        var srcY = 0f
        var dstX = 0f
        var dstY = 0f
        for (p in src) {
            srcX += p.x
            srcY += p.y
        }
        for (p in dst) {
            dstX += p.x
            dstY += p.y
        }
        val n = src.size.toFloat()
        srcX /= n
        srcY /= n
        dstX /= n
        dstY /= n

        var numA = 0f
        var numB = 0f
        var den = 0f
        for (i in src.indices) {
            val sx = src[i].x - srcX
            val sy = src[i].y - srcY
            val dx = dst[i].x - dstX
            val dy = dst[i].y - dstY
            numA += sx * dx + sy * dy
            numB += sx * dy - sy * dx
            den += sx * sx + sy * sy
        }
        if (den < MIN_SPREAD) return null
        val a = numA / den
        val b = numB / den
        val tx = dstX - (a * srcX - b * srcY)
        val ty = dstY - (b * srcX + a * srcY)
        return Similarity(a, b, tx, ty)
    }

    private fun distance(a: Point, b: Point): Float {
        val dx = a.x - b.x
        val dy = a.y - b.y
        return sqrt(dx * dx + dy * dy)
    }

    private const val MIN_EYE_DISTANCE = 1f
    private const val MIN_SPREAD = 1e-4f
}
