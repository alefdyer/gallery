package com.asinosoft.photogallery.ui.editor

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.asinosoft.photogallery.R
import kotlin.math.min

internal enum class AspectMark {
    None,
    Corners,
    Instagram,
    Story,
    Facebook,
    Pinterest,
    YouTube,
    Twitter,
    Phone,
}

internal data class AspectPreset(
    val id: String,
    val label: String? = null,
    val labelRes: Int = 0,
    val aspect: Float? = null,
    val useScreen: Boolean = false,
    val mark: AspectMark = AspectMark.None,
    val iconScale: Float = 1f,
)

private const val A_PAPER = 0.70710677f

internal fun aspectPresets(): List<AspectPreset> = listOf(
    AspectPreset("free", labelRes = R.string.editor_aspect_free, mark = AspectMark.Corners),
    AspectPreset("3:4", label = "3:4", aspect = 3f / 4f),
    AspectPreset("4:3", label = "4:3", aspect = 4f / 3f),
    AspectPreset("5:4", label = "5:4", aspect = 5f / 4f),
    AspectPreset("ig11", label = "IG 1:1", aspect = 1f, mark = AspectMark.Instagram),
    AspectPreset("ig45", label = "IG 4:5", aspect = 4f / 5f, mark = AspectMark.Instagram),
    AspectPreset("story", label = "Ins Story", aspect = 9f / 16f, mark = AspectMark.Story),
    AspectPreset("movie", label = "Movie", aspect = 2.35f),
    AspectPreset("1:2", label = "1:2", aspect = 1f / 2f),
    AspectPreset("2:3", label = "2:3", aspect = 2f / 3f),
    AspectPreset("3:2", label = "3:2", aspect = 3f / 2f),
    AspectPreset("9:16", label = "9:16", aspect = 9f / 16f),
    AspectPreset("16:9", label = "16:9", aspect = 16f / 9f),
    AspectPreset("fbPost", label = "Post", aspect = 1.91f, mark = AspectMark.Facebook),
    AspectPreset("fbCover", label = "Cover", aspect = 2.63f, mark = AspectMark.Facebook),
    AspectPreset("pin", label = "Post", aspect = 2f / 3f, mark = AspectMark.Pinterest),
    AspectPreset("a4", label = "A4", aspect = A_PAPER),
    AspectPreset("a5", label = "A5", aspect = A_PAPER, iconScale = 0.72f),
    AspectPreset(
        "screen",
        labelRes = R.string.editor_aspect_screen,
        useScreen = true,
        mark = AspectMark.Phone,
    ),
    AspectPreset("yt", label = "Cover", aspect = 16f / 9f, mark = AspectMark.YouTube),
    AspectPreset("twPost", label = "Post", aspect = 16f / 9f, mark = AspectMark.Twitter),
    AspectPreset("twHeader", label = "Header", aspect = 3f, mark = AspectMark.Twitter),
)

@Composable
internal fun AspectPresetIcon(
    preset: AspectPreset,
    selected: Boolean,
    screenAspect: Float,
    modifier: Modifier = Modifier,
) {
    val color = if (selected) Color(0xFFFFD54F) else Color(0xFFB0B0B0)
    val letter = when (preset.mark) {
        AspectMark.Facebook -> "f"
        AspectMark.Pinterest -> "P"
        else -> null
    }
    Box(modifier.size(40.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(40.dp)) {
            val ratio = when {
                preset.useScreen -> screenAspect.coerceIn(0.35f, 3.2f)
                preset.mark == AspectMark.Corners -> 1f
                else -> (preset.aspect ?: 1f).coerceIn(0.35f, 3.2f)
            }
            val box = size.minDimension * 0.86f * preset.iconScale
            val (width, height) = fitted(box, ratio)
            val left = (size.width - width) / 2f
            val top = (size.height - height) / 2f
            val stroke = Stroke(width = 1.5.dp.toPx(), cap = StrokeCap.Round)
            val radius = min(5.dp.toPx(), min(width, height) / 4f)
            if (preset.mark == AspectMark.Corners) {
                drawCornerMarks(left, top, width, height, color, stroke.width)
            } else {
                drawRoundRect(
                    color = color,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    cornerRadius = CornerRadius(radius, radius),
                    style = stroke,
                )
            }
            val glyph = min(width, height) * 0.72f
            when (preset.mark) {
                AspectMark.Instagram, AspectMark.Story ->
                    drawInstagram(left + width / 2f, top + height / 2f, glyph, color)

                AspectMark.YouTube ->
                    drawYouTube(left + width / 2f, top + height / 2f, glyph, color)

                AspectMark.Twitter ->
                    drawGlyph(twitterLogo, left + width / 2f, top + height / 2f, glyph, color)

                AspectMark.Phone ->
                    drawCircle(
                        color = color,
                        radius = min(width, height) * 0.06f,
                        center = Offset(left + width / 2f, top + height * 0.78f),
                    )

                else -> Unit
            }
        }
        if (letter != null) {
            Text(
                text = letter,
                color = color,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

private fun fitted(box: Float, aspect: Float): Pair<Float, Float> {
    return if (aspect >= 1f) box to box / aspect else box * aspect to box
}

private fun DrawScope.drawCornerMarks(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    color: Color,
    strokeWidth: Float,
) {
    val arm = min(width, height) * 0.28f
    val right = left + width
    val bottom = top + height
    fun segment(from: Offset, to: Offset) {
        drawLine(color, from, to, strokeWidth = strokeWidth, cap = StrokeCap.Round)
    }
    segment(Offset(left, top + arm), Offset(left, top))
    segment(Offset(left, top), Offset(left + arm, top))
    segment(Offset(right - arm, top), Offset(right, top))
    segment(Offset(right, top), Offset(right, top + arm))
    segment(Offset(right, bottom - arm), Offset(right, bottom))
    segment(Offset(right, bottom), Offset(right - arm, bottom))
    segment(Offset(left + arm, bottom), Offset(left, bottom))
    segment(Offset(left, bottom), Offset(left, bottom - arm))
}

private val twitterLogo = PathParser().parsePathString(
    "M23.64,4.94c-0.84,0.37 -1.73,0.62 -2.68,0.73c0.96,-0.58 1.7,-1.49 2.05,-2.58" +
            "c-0.9,0.53 -1.9,0.92 -2.96,1.13c-0.85,-0.9 -2.06,-1.47 -3.4,-1.47c-2.57,0 -4.66,2.09 -4.66,4.66" +
            "c0,0.36 0.04,0.72 0.12,1.06c-3.87,-0.2 -7.3,-2.05 -9.6,-4.87c-0.4,0.69 -0.63,1.49 -0.63,2.34" +
            "c0,1.62 0.82,3.04 2.07,3.88c-0.76,-0.02 -1.48,-0.23 -2.11,-0.58v0.06c0,2.26 1.61,4.14 3.74,4.57" +
            "c-0.39,0.11 -0.8,0.16 -1.23,0.16c-0.3,0 -0.59,-0.03 -0.88,-0.08c0.59,1.85 2.31,3.2 4.35,3.23" +
            "c-1.6,1.25 -3.6,2 -5.79,2c-0.38,0 -0.75,-0.02 -1.11,-0.07c2.06,1.32 4.51,2.09 7.14,2.09" +
            "c8.57,0 13.26,-7.1 13.26,-13.25c0,-0.2 0,-0.4 -0.01,-0.6c0.91,-0.66 1.7,-1.48 2.32,-2.41z"
).toPath()

private fun DrawScope.drawInstagram(cx: Float, cy: Float, size: Float, color: Color) {
    val stroke = Stroke(width = size * 0.08f)
    drawRoundRect(
        color = color,
        topLeft = Offset(cx - size / 2f, cy - size / 2f),
        size = Size(size, size),
        cornerRadius = CornerRadius(size * 0.28f, size * 0.28f),
        style = stroke,
    )
    drawCircle(
        color = color,
        radius = size * 0.18f,
        center = Offset(cx, cy),
        style = stroke,
    )
    drawCircle(
        color = color,
        radius = size * 0.055f,
        center = Offset(cx + size * 0.26f, cy - size * 0.26f),
    )
}

private fun DrawScope.drawYouTube(cx: Float, cy: Float, size: Float, color: Color) {
    val width = size * 1.35f
    val height = size * 0.95f
    val left = cx - width / 2f
    val top = cy - height / 2f
    val triangle = Path().apply {
        moveTo(cx - width * 0.12f, cy - height * 0.24f)
        lineTo(cx - width * 0.12f, cy + height * 0.24f)
        lineTo(cx + width * 0.2f, cy)
        close()
    }
    drawIntoCanvas { canvas ->
        canvas.saveLayer(androidx.compose.ui.geometry.Rect(Offset.Zero, this.size), Paint())
        drawRoundRect(
            color = color,
            topLeft = Offset(left, top),
            size = Size(width, height),
            cornerRadius = CornerRadius(height * 0.28f, height * 0.28f),
        )
        drawPath(triangle, Color.Black, blendMode = BlendMode.Clear)
        canvas.restore()
    }
}

private fun DrawScope.drawGlyph(glyph: Path, cx: Float, cy: Float, size: Float, color: Color) {
    val scale = size / 24f
    translate(cx - 12f * scale, cy - 12f * scale) {
        scale(scale, scale, pivot = Offset.Zero) {
            drawPath(glyph, color)
        }
    }
}
