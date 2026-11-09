package com.asinosoft.photogallery.ui.editor

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Padding шаблона в долях размера текста (как dp при textSize≈54). */
internal fun templatePadPx(templateId: String, textSize: Float): Pair<Float, Float> {
    val unit = textSize / 54f
    return when (templateId) {
        "dash" -> 10f * unit to 8f * unit
        "bubble" -> 10f * unit to 11f * unit
        "round" -> 12f * unit to 10f * unit
        "rect" -> 8f * unit to 9f * unit
        "oval" -> 12f * unit to 7f * unit
        "tag" -> 14f * unit to 5f * unit
        "frame" -> 10f * unit to 8f * unit
        "cloud", "boom" -> 12f * unit to 8f * unit
        "xmas" -> 10f * unit to 7f * unit
        "date" -> 8f * unit to 3f * unit
        "paint" -> 10f * unit to 6f * unit
        "makeup" -> 8f * unit to 3f * unit
        "floral", "love" -> 12f * unit to 10f * unit
        else -> 0f to 0f
    }
}

internal fun paintTemplateBackground(
    canvas: Canvas,
    templateId: String,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
) {
    val w = right - left
    val h = bottom - top
    if (w < 2f || h < 2f || templateId == "plain") return
    canvas.save()
    canvas.translate(left, top)
    val ink = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF212121.toInt()
        style = Paint.Style.STROKE
        strokeWidth = maxOf(1.2f, min(w, h) * 0.02f)
    }
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    when (templateId) {
        "dash" -> {
            ink.pathEffect = DashPathEffect(floatArrayOf(7f, 5f), 0f)
            canvas.drawRoundRect(
                RectF(w * 0.04f, h * 0.08f, w * 0.96f, h * 0.92f),
                3f,
                3f,
                ink,
            )
        }

        "bubble" -> speechAndroid(
            canvas,
            w * 0.02f,
            h * 0.04f,
            w * 0.96f,
            h * 0.72f,
            min(w, h) * 0.12f,
            w * 0.18f,
            h * 0.96f,
            w * 0.16f,
            fill,
            ink,
        )

        "round" -> {
            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawOval(RectF(w * 0.06f, h * 0.02f, w * 0.94f, h * 0.80f), fill)
            val tail = Path().apply {
                moveTo(w * 0.42f, h * 0.70f)
                lineTo(w * 0.40f, h * 0.98f)
                lineTo(w * 0.62f, h * 0.72f)
                close()
            }
            canvas.drawPath(tail, fill)
            canvas.drawOval(RectF(w * 0.06f, h * 0.02f, w * 0.94f, h * 0.80f), ink)
            canvas.drawLine(w * 0.42f, h * 0.74f, w * 0.40f, h * 0.98f, ink)
            canvas.drawLine(w * 0.40f, h * 0.98f, w * 0.60f, h * 0.76f, ink)
        }

        "rect" -> speechAndroid(
            canvas,
            w * 0.02f,
            h * 0.08f,
            w * 0.98f,
            h * 0.74f,
            min(w, h) * 0.04f,
            w * 0.16f,
            h * 0.98f,
            w * 0.10f,
            fill,
            ink,
        )

        "oval" -> {
            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawOval(RectF(w * 0.02f, h * 0.12f, w * 0.98f, h * 0.82f), fill)
            val tail = Path().apply {
                moveTo(w * 0.78f, h * 0.70f)
                lineTo(w * 0.92f, h * 0.96f)
                lineTo(w * 0.70f, h * 0.72f)
                close()
            }
            canvas.drawPath(tail, fill)
            canvas.drawOval(RectF(w * 0.02f, h * 0.12f, w * 0.98f, h * 0.82f), ink)
            canvas.drawLine(w * 0.80f, h * 0.74f, w * 0.92f, h * 0.96f, ink)
            canvas.drawLine(w * 0.92f, h * 0.96f, w * 0.72f, h * 0.76f, ink)
        }

        "tag" -> {
            val path = Path().apply {
                moveTo(w * 0.02f, h * 0.16f)
                lineTo(w * 0.78f, h * 0.16f)
                lineTo(w * 0.98f, h * 0.50f)
                lineTo(w * 0.78f, h * 0.84f)
                lineTo(w * 0.02f, h * 0.84f)
                close()
            }
            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawPath(path, fill)
            canvas.drawPath(path, ink)
        }

        "frame" -> {
            ink.color = 0xFF64B5F6.toInt()
            canvas.drawRoundRect(
                RectF(w * 0.08f, h * 0.20f, w * 0.96f, h * 0.90f),
                1f,
                1f,
                ink,
            )
            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawRoundRect(
                RectF(w * 0.02f, h * 0.08f, w * 0.88f, h * 0.78f),
                1f,
                1f,
                fill,
            )
            ink.color = 0xFF212121.toInt()
            ink.strokeWidth = maxOf(1.6f, min(w, h) * 0.025f)
            canvas.drawRoundRect(
                RectF(w * 0.02f, h * 0.08f, w * 0.88f, h * 0.78f),
                1f,
                1f,
                ink,
            )
        }

        "cloud" -> {
            val path = cloudPathAndroid(w, h)
            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawPath(path, fill)
            canvas.drawPath(path, ink)
        }

        "boom" -> {
            val path = burstPathAndroid(w, h)
            fill.color = 0xFFFFFFFF.toInt()
            canvas.drawPath(path, fill)
            canvas.drawPath(path, ink)
        }

        "floral" -> {
            flowerAndroid(canvas, fill, w * 0.10f, h * 0.22f, min(w, h) * 0.16f, 0xFFF48FB1.toInt())
            flowerAndroid(canvas, fill, w * 0.24f, h * 0.12f, min(w, h) * 0.11f, 0xFFF8BBD0.toInt())
            flowerAndroid(canvas, fill, w * 0.88f, h * 0.78f, min(w, h) * 0.14f, 0xFFCE93D8.toInt())
            flowerAndroid(canvas, fill, w * 0.74f, h * 0.90f, min(w, h) * 0.09f, 0xFFF48FB1.toInt())
        }

        "xmas" -> {
            ink.color = 0xFF2E7D32.toInt()
            ink.strokeWidth = maxOf(1.4f, min(w, h) * 0.022f)
            canvas.drawRoundRect(
                RectF(w * 0.04f, h * 0.08f, w * 0.96f, h * 0.92f),
                2f,
                2f,
                ink,
            )
            ink.pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
            ink.strokeWidth = maxOf(1f, min(w, h) * 0.015f)
            canvas.drawRoundRect(
                RectF(w * 0.09f, h * 0.16f, w * 0.91f, h * 0.84f),
                2f,
                2f,
                ink,
            )
            hollyAndroid(canvas, fill, w * 0.12f, h * 0.16f, min(w, h))
            hollyAndroid(canvas, fill, w * 0.88f, h * 0.84f, min(w, h))
        }

        "love" -> {
            val s = min(w, h) * 0.16f
            heartAndroid(canvas, fill, w * 0.08f, h * 0.18f, s, 0xFFE53935.toInt())
            heartAndroid(canvas, fill, w * 0.92f, h * 0.16f, s * 0.8f, 0xFFEC407A.toInt())
            heartAndroid(canvas, fill, w * 0.10f, h * 0.86f, s * 0.7f, 0xFFEC407A.toInt())
            heartAndroid(canvas, fill, w * 0.90f, h * 0.84f, s, 0xFFE53935.toInt())
        }

        "date" -> {
            fill.color = 0xFF000000.toInt()
            canvas.drawRoundRect(RectF(0f, 0f, w, h), 2f, 2f, fill)
        }

        "paint" -> {
            ink.color = 0xFFFFFFFF.toInt()
            canvas.drawOval(RectF(w * 0.08f, h * 0.10f, w * 0.88f, h * 0.88f), ink)
            canvas.drawOval(RectF(w * 0.16f, h * 0.16f, w * 0.92f, h * 0.86f), ink)
        }

        "makeup" -> {
            fill.color = 0xFFF48FB1.toInt()
            canvas.drawRoundRect(
                RectF(0f, h * 0.28f, w, h * 0.74f),
                h,
                h,
                fill,
            )
        }
    }
    canvas.restore()
}

private fun speechAndroid(
    canvas: Canvas,
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    tailX: Float,
    tailY: Float,
    tailWidth: Float,
    fill: Paint,
    ink: Paint,
) {
    fill.color = 0xFFFFFFFF.toInt()
    canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, fill)
    val tail = Path().apply {
        moveTo(left + tailWidth, bottom - 1f)
        lineTo(tailX, tailY)
        lineTo(left + tailWidth * 2.4f, bottom - 1f)
        close()
    }
    canvas.drawPath(tail, fill)
    canvas.drawRoundRect(RectF(left, top, right, bottom), radius, radius, ink)
    canvas.drawLine(left + tailWidth, bottom, tailX, tailY, ink)
    canvas.drawLine(tailX, tailY, left + tailWidth * 2.4f, bottom, ink)
}

private fun cloudPathAndroid(w: Float, h: Float) = Path().apply {
    moveTo(w * 0.22f, h * 0.62f)
    cubicTo(w * 0.04f, h * 0.64f, w * 0.04f, h * 0.36f, w * 0.26f, h * 0.34f)
    cubicTo(w * 0.30f, h * 0.12f, w * 0.55f, h * 0.08f, w * 0.60f, h * 0.32f)
    cubicTo(w * 0.82f, h * 0.20f, w * 0.98f, h * 0.40f, w * 0.84f, h * 0.58f)
    cubicTo(w * 0.98f, h * 0.74f, w * 0.78f, h * 0.92f, w * 0.56f, h * 0.78f)
    cubicTo(w * 0.46f, h * 0.94f, w * 0.24f, h * 0.90f, w * 0.22f, h * 0.70f)
    close()
}

private fun burstPathAndroid(w: Float, h: Float) = Path().apply {
    val cx = w * 0.50f
    val cy = h * 0.50f
    val spikes = 14
    for (i in 0 until spikes) {
        val angle = -Math.PI / 2.0 + i * 2.0 * Math.PI / spikes
        val outer = i % 2 == 0
        val rx = w * if (outer) 0.50f else 0.30f
        val ry = h * if (outer) 0.48f else 0.28f
        val x = cx + cos(angle).toFloat() * rx
        val y = cy + sin(angle).toFloat() * ry
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun flowerAndroid(
    canvas: Canvas,
    paint: Paint,
    cx: Float,
    cy: Float,
    radius: Float,
    color: Int,
) {
    paint.style = Paint.Style.FILL
    paint.color = color
    for (i in 0 until 5) {
        val angle = -Math.PI / 2.0 + i * 2.0 * Math.PI / 5
        val x = cx + cos(angle).toFloat() * radius * 0.7f
        val y = cy + sin(angle).toFloat() * radius * 0.7f
        canvas.drawCircle(x, y, radius * 0.55f, paint)
    }
    paint.color = 0xFFFFF176.toInt()
    canvas.drawCircle(cx, cy, radius * 0.38f, paint)
}

private fun hollyAndroid(canvas: Canvas, paint: Paint, x: Float, y: Float, ref: Float) {
    val u = ref * 0.05f
    paint.style = Paint.Style.FILL
    paint.color = 0xFF2E7D32.toInt()
    canvas.drawCircle(x - u, y, u * 1.1f, paint)
    paint.color = 0xFF388E3C.toInt()
    canvas.drawCircle(x + u * 0.7f, y - u * 0.3f, u * 1.1f, paint)
    paint.color = 0xFFE53935.toInt()
    canvas.drawCircle(x, y + u * 0.7f, u * 0.6f, paint)
}

private fun heartAndroid(
    canvas: Canvas,
    paint: Paint,
    cx: Float,
    cy: Float,
    sizePx: Float,
    color: Int,
) {
    paint.style = Paint.Style.FILL
    paint.color = color
    val s = sizePx
    val path = Path().apply {
        moveTo(cx, cy + s * 0.35f)
        cubicTo(cx - s, cy - s * 0.35f, cx - s * 0.35f, cy - s * 0.7f, cx, cy - s * 0.15f)
        cubicTo(cx + s * 0.35f, cy - s * 0.7f, cx + s, cy - s * 0.35f, cx, cy + s * 0.35f)
        close()
    }
    canvas.drawPath(path, paint)
}
