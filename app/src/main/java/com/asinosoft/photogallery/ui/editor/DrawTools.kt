package com.asinosoft.photogallery.ui.editor

import android.graphics.DashPathEffect
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import com.asinosoft.photogallery.R
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

private val Ink = Color(0xFF2A2A2A)
private val Amber = Color(0xFFFFD54F)
private val Track = Color(0xFF3A3A3A)

val drawPalette: List<Long> = listOf(
    0xFFFFFFFF,
    0xFF000000,
    0xFF616161,
    0xFFBDBDBD,
    0xFF8C6E63,
    0xFFFEE082,
    0xFF8A201B,
    0xFF8B411A,
    0xFF886718,
    0xFF77891B,
    0xFF1A892B,
    0xFF198971,
    0xFF1B6787,
    0xFF192C8A,
    0xFF5A1A8C,
    0xFFB42922,
    0xFFB25722,
    0xFFB38822,
    0xFF9BB223,
    0xFF22B33A,
    0xFF21B497,
    0xFF2186B2,
    0xFF243AB4,
    0xFF7722B3,
    0xFFD43C2F,
    0xFFD66E31,
    0xFFD7A532,
    0xFFBDD831,
    0xFF30D84D,
    0xFF32D7B5,
    0xFF30A5DA,
    0xFF314CD7,
    0xFF9130D7,
    0xFFE26D64,
    0xFFE09264,
    0xFFE0BB63,
    0xFFCCE063,
    0xFF62E178,
    0xFF65E1C9,
    0xFF63BBDF,
    0xFF6479E2,
    0xFFAC64E1,
    0xFFEB9E98,
    0xFFE9B697,
    0xFFEBD299,
    0xFFDDEA98,
    0xFF99EAA8,
    0xFF99ECDC,
    0xFF9BD3EC,
    0xFF98A7EC,
    0xFFC899E9,
    0xFFF3C6C3,
    0xFFF3D3C4,
    0xFFF4E6C3,
    0xFFEBF3C2,
    0xFFC4F3CD,
    0xFFC4F3EA,
    0xFFC3E5F2,
    0xFFC3CBF1,
    0xFFDFC2F3,
)

fun DrawStyle.isShape(): Boolean = this == DrawStyle.LINE || this == DrawStyle.RECT ||
        this == DrawStyle.OVAL || this == DrawStyle.ARROW

fun brushWidth(settings: DrawSettings): Float = 0.006f + settings.size / 100f * 0.07f

fun strokeFor(settings: DrawSettings, points: List<StrokePoint>) = DrawStroke(
    points = points,
    basePoints = points,
    baseSize = settings.size,
    color = settings.color,
    width = brushWidth(settings),
    style = settings.style,
    opacity = settings.opacity.coerceIn(0, 100) / 100f,
)

fun moveShapeHandle(stroke: DrawStroke, index: Int, point: StrokePoint, size: Int): DrawStroke {
    val next = when (stroke.style) {
        DrawStyle.LINE, DrawStyle.ARROW -> {
            val points = stroke.points.toMutableList()
            if (index in points.indices) points[index] = point
            points
        }

        else -> {
            val box = bounds(stroke.points)
            var left = box[0]
            var top = box[1]
            var right = box[2]
            var bottom = box[3]
            when (index) {
                0 -> {
                    left = point.x; top = point.y
                }

                1 -> {
                    right = point.x; top = point.y
                }

                2 -> {
                    right = point.x; bottom = point.y
                }

                else -> {
                    left = point.x; bottom = point.y
                }
            }
            listOf(StrokePoint(left, top), StrokePoint(right, bottom))
        }
    }
    return stroke.copy(points = next, basePoints = next, baseSize = size.coerceAtLeast(1))
}

fun shapeHandles(stroke: DrawStroke): List<StrokePoint> = when (stroke.style) {
    DrawStyle.LINE, DrawStyle.ARROW -> stroke.points.take(2)
    DrawStyle.RECT, DrawStyle.OVAL -> {
        val box = bounds(stroke.points)
        listOf(
            StrokePoint(box[0], box[1]),
            StrokePoint(box[2], box[1]),
            StrokePoint(box[2], box[3]),
            StrokePoint(box[0], box[3]),
        )
    }

    else -> emptyList()
}

fun shapeHit(stroke: DrawStroke, point: StrokePoint, radius: Float): Boolean {
    if (stroke.points.size < 2) return false
    val a = stroke.points.first()
    val b = stroke.points.last()
    return when (stroke.style) {
        DrawStyle.LINE, DrawStyle.ARROW -> distanceToSegment(point, a, b) <= radius
        DrawStyle.RECT, DrawStyle.OVAL -> {
            val box = bounds(stroke.points)
            point.x in box[0] - radius..box[2] + radius && point.y in box[1] - radius..box[3] + radius
        }

        else -> false
    }
}

fun hitHandle(stroke: DrawStroke, point: StrokePoint, radius: Float): Int? {
    shapeHandles(stroke).forEachIndexed { index, handle ->
        val dx = handle.x - point.x
        val dy = handle.y - point.y
        if (dx * dx + dy * dy <= radius * radius) return index
    }
    return null
}

@Composable
fun DrawToolbar(
    settings: DrawSettings,
    onChange: (DrawSettings) -> Unit,
    modifier: Modifier = Modifier,
    topRowHeight: Dp? = null,
) {
    var menu by remember { mutableStateOf(false) }
    var colors by remember { mutableStateOf(false) }
    Column(modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .then(
                    if (topRowHeight != null) Modifier
                        .height(topRowHeight)
                        .clipToBounds() else Modifier
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Ink)
                        .clickable { menu = !menu; colors = false }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        stringResource(
                            if (settings.slider == DrawSlider.SIZE) R.string.editor_brush_size
                            else R.string.editor_brush_opacity
                        ),
                        color = Color.White,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("▾", color = Color.White, fontSize = 11.sp)
                }
                if (menu) {
                    Popup(
                        alignment = Alignment.TopStart,
                        onDismissRequest = { menu = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Column(
                            Modifier
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Ink)
                                .padding(vertical = 6.dp),
                        ) {
                            SliderChoice(
                                label = stringResource(R.string.editor_brush_size),
                                selected = settings.slider == DrawSlider.SIZE,
                                sizeIcon = true,
                            ) {
                                onChange(settings.copy(slider = DrawSlider.SIZE))
                                menu = false
                            }
                            SliderChoice(
                                label = stringResource(R.string.editor_brush_opacity),
                                selected = settings.slider == DrawSlider.OPACITY,
                                sizeIcon = false,
                            ) {
                                onChange(settings.copy(slider = DrawSlider.OPACITY))
                                menu = false
                            }
                        }
                    }
                }
            }
            DrawSliderBar(
                value = if (settings.slider == DrawSlider.SIZE) settings.size else settings.opacity,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
            ) { value ->
                onChange(
                    if (settings.slider == DrawSlider.SIZE) settings.copy(size = value)
                    else settings.copy(opacity = value)
                )
            }
            Text(
                text = if (settings.slider == DrawSlider.SIZE) settings.size.toString() else settings.opacity.toString(),
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.width(28.dp),
            )
            Box {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(Ink)
                        .clickable { colors = !colors; menu = false }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Box(
                        Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(settings.color)),
                    )
                }
                if (colors) {
                    Popup(
                        alignment = Alignment.TopEnd,
                        onDismissRequest = { colors = false },
                        properties = PopupProperties(focusable = true),
                    ) {
                        Row(
                            Modifier
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Ink)
                                .horizontalScroll(rememberScrollState())
                                .padding(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF121212))
                                    .clickable {
                                        colors = false
                                        onChange(settings.copy(picking = true))
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Canvas(Modifier.size(22.dp)) { drawSvg(DropperPath) }
                            }
                            drawPalette.forEach { color ->
                                val selected = color == settings.color && !settings.picking
                                Box(
                                    Modifier
                                        .size(36.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(color))
                                        .then(
                                            if (selected) Modifier.border(
                                                2.dp,
                                                Amber,
                                                RoundedCornerShape(10.dp)
                                            )
                                            else Modifier
                                        )
                                        .clickable {
                                            onChange(settings.copy(color = color, picking = false))
                                            colors = false
                                        },
                                )
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(
            Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DrawStyle.entries.forEach { style ->
                val selected = settings.style == style
                Box(
                    Modifier
                        .size(46.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Ink)
                        .then(
                            if (selected) Modifier.padding(0.dp) else Modifier
                        )
                        .clickable { onChange(settings.copy(style = style, picking = false)) },
                    contentAlignment = Alignment.Center,
                ) {
                    Canvas(Modifier.size(26.dp)) { drawStyleIcon(style) }
                    if (selected) {
                        Canvas(Modifier.matchParentSize()) {
                            drawRoundRect(
                                Amber,
                                style = Stroke(width = 2.dp.toPx()),
                                cornerRadius = CornerRadius(12.dp.toPx(), 12.dp.toPx()),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SliderChoice(label: String, selected: Boolean, sizeIcon: Boolean, onClick: () -> Unit) {
    val tint = if (selected) Amber else Color.White
    Row(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(Modifier.size(22.dp)) {
            if (sizeIcon) drawBrushMark(tint) else drawFadeMark(tint)
        }
        Spacer(Modifier.width(12.dp))
        Text(label, color = tint, fontSize = 16.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DrawSliderBar(value: Int, modifier: Modifier, onChange: (Int) -> Unit) {
    val change = rememberUpdatedState(onChange)
    Canvas(
        modifier
            .height(28.dp)
            .pointerInput(Unit) {
                fun at(x: Float) {
                    val t = (x / size.width).coerceIn(0f, 1f)
                    change.value((1 + t * 99f).toInt().coerceIn(1, 100))
                }
                detectHorizontalDragGestures(
                    onDragStart = { at(it.x) },
                    onHorizontalDrag = { change, _ -> at(change.position.x) },
                )
            },
    ) {
        val y = size.height / 2f
        val t = ((value - 1) / 99f).coerceIn(0f, 1f)
        val x = t * size.width
        drawLine(
            Track,
            Offset(0f, y),
            Offset(size.width, y),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawLine(
            Amber,
            Offset(0f, y),
            Offset(x, y),
            strokeWidth = 3.dp.toPx(),
            cap = StrokeCap.Round
        )
        drawCircle(Amber, 9.dp.toPx(), Offset(x, y))
    }
}

@Composable
fun DrawStrokesOverlay(
    strokes: List<DrawStroke>,
    draft: DrawStroke?,
    selectedId: Long?,
    contentFrame: OverlayContentFrame? = null,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val ox = contentFrame?.left ?: 0f
        val oy = contentFrame?.top ?: 0f
        val cw = contentFrame?.width?.takeIf { it > 1f } ?: size.width
        val ch = contentFrame?.height?.takeIf { it > 1f } ?: size.height
        strokes.forEach { paintUserStroke(it, cw, ch, ox, oy) }
        draft?.let { paintUserStroke(it, cw, ch, ox, oy) }
        strokes.find { it.id == selectedId && it.style.isShape() }?.let { stroke ->
            shapeHandles(stroke).forEach { handle ->
                val at = Offset(ox + handle.x * cw, oy + handle.y * ch)
                drawCircle(Amber, 7.dp.toPx(), at)
                drawCircle(Color.White, 3.dp.toPx(), at)
            }
        }
    }
}

@Composable
fun ColorAim(at: Offset, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        drawCircle(Color.White, 18.dp.toPx(), at, style = Stroke(1.5.dp.toPx()))
        drawLine(
            Amber,
            Offset(at.x - 26.dp.toPx(), at.y),
            Offset(at.x + 26.dp.toPx(), at.y),
            1.5.dp.toPx()
        )
        drawLine(
            Amber,
            Offset(at.x, at.y - 26.dp.toPx()),
            Offset(at.x, at.y + 26.dp.toPx()),
            1.5.dp.toPx()
        )
    }
}

private fun DrawScope.paintUserStroke(
    stroke: DrawStroke,
    contentWidth: Float,
    contentHeight: Float,
    originX: Float,
    originY: Float,
) {
    if (stroke.points.size < 2 || stroke.style == DrawStyle.ERASER) return
    val color = inkColor(stroke)
    val widthPx = stroke.width * contentWidth
    when (stroke.style) {
        DrawStyle.DOTTED -> strokePath(
            stroke, widthPx, color, PathEffect.dashPathEffect(dashIntervals(widthPx)),
            contentWidth, contentHeight, originX, originY,
        )

        DrawStyle.NEON -> {
            strokePath(
                stroke, widthPx * 3.2f, color.copy(alpha = color.alpha * 0.35f), null,
                contentWidth, contentHeight, originX, originY,
            )
            strokePath(
                stroke, widthPx, color, null,
                contentWidth, contentHeight, originX, originY,
            )
        }

        DrawStyle.STAMPS -> drawStamps(
            stroke, color, widthPx, contentWidth, contentHeight, originX, originY,
        )

        DrawStyle.LINE -> lineEnds(
            stroke, widthPx, color, arrow = false,
            contentWidth, contentHeight, originX, originY,
        )

        DrawStyle.ARROW -> lineEnds(
            stroke, widthPx, color, arrow = true,
            contentWidth, contentHeight, originX, originY,
        )

        DrawStyle.RECT -> {
            val box = pixelBounds(stroke, contentWidth, contentHeight, originX, originY)
            drawRoundRect(
                color,
                Offset(box[0], box[1]),
                Size(box[2] - box[0], box[3] - box[1]),
                CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = Stroke(widthPx, cap = StrokeCap.Round)
            )
        }

        DrawStyle.OVAL -> {
            val box = pixelBounds(stroke, contentWidth, contentHeight, originX, originY)
            drawOval(
                color,
                Offset(box[0], box[1]),
                Size(box[2] - box[0], box[3] - box[1]),
                style = Stroke(widthPx, cap = StrokeCap.Round)
            )
        }

        else -> strokePath(
            stroke, widthPx, color, null,
            contentWidth, contentHeight, originX, originY,
        )
    }
}

private fun DrawScope.strokePath(
    stroke: DrawStroke,
    widthPx: Float,
    color: Color,
    effect: PathEffect?,
    contentWidth: Float,
    contentHeight: Float,
    originX: Float,
    originY: Float,
) {
    val path = Path()
    val first = stroke.points.first()
    path.moveTo(originX + first.x * contentWidth, originY + first.y * contentHeight)
    stroke.points.drop(1).forEach {
        path.lineTo(originX + it.x * contentWidth, originY + it.y * contentHeight)
    }
    drawPath(
        path,
        color,
        style = Stroke(widthPx, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect)
    )
}

private fun DrawScope.lineEnds(
    stroke: DrawStroke,
    widthPx: Float,
    color: Color,
    arrow: Boolean,
    contentWidth: Float,
    contentHeight: Float,
    originX: Float,
    originY: Float,
) {
    val a = stroke.points.first()
    val b = stroke.points.last()
    val start = Offset(originX + a.x * contentWidth, originY + a.y * contentHeight)
    val end = Offset(originX + b.x * contentWidth, originY + b.y * contentHeight)
    drawLine(color, start, end, widthPx, StrokeCap.Round)
    if (!arrow) return
    val angle = atan2(end.y - start.y, end.x - start.x)
    val head = widthPx * 4f + 10.dp.toPx()
    val path = Path().apply {
        moveTo(end.x, end.y)
        lineTo(end.x - head * cos(angle - 0.45f), end.y - head * sin(angle - 0.45f))
        lineTo(end.x - head * cos(angle + 0.45f), end.y - head * sin(angle + 0.45f))
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawStamps(
    stroke: DrawStroke,
    color: Color,
    widthPx: Float,
    contentWidth: Float,
    contentHeight: Float,
    originX: Float,
    originY: Float,
) {
    val faces = arrayOf("😣", "🙂", "😒", "😄")
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        textSize = (widthPx * 2.2f).coerceAtLeast(18.dp.toPx())
        textAlign = android.graphics.Paint.Align.CENTER
    }
    var index = 0
    var previous: StrokePoint? = null
    var walked = 0f
    val step = paint.textSize * 1.15f
    stroke.points.forEach { point ->
        val last = previous
        if (last != null) {
            val dx = (point.x - last.x) * contentWidth
            val dy = (point.y - last.y) * contentHeight
            walked += hypot(dx, dy)
        }
        if (previous == null || walked >= step) {
            drawContext.canvas.nativeCanvas.drawText(
                faces[index % faces.size],
                originX + point.x * contentWidth,
                originY + point.y * contentHeight,
                paint,
            )
            index++
            walked = 0f
        }
        previous = point
    }
}

private fun pixelBounds(
    stroke: DrawStroke,
    contentWidth: Float,
    contentHeight: Float,
    originX: Float,
    originY: Float,
): FloatArray {
    val box = bounds(stroke.points)
    return floatArrayOf(
        originX + box[0] * contentWidth,
        originY + box[1] * contentHeight,
        originX + box[2] * contentWidth,
        originY + box[3] * contentHeight,
    )
}

fun paintStroke(canvas: android.graphics.Canvas, stroke: DrawStroke, width: Int, height: Int) {
    if (stroke.points.size < 2 || stroke.style == DrawStyle.ERASER) return
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        style = android.graphics.Paint.Style.STROKE
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        color = androidInk(stroke)
        strokeWidth = stroke.width * width
    }
    val path = androidPath(stroke, width, height)
    when (stroke.style) {
        DrawStyle.DOTTED -> {
            paint.pathEffect = DashPathEffect(dashIntervals(paint.strokeWidth), 0f)
            canvas.drawPath(path, paint)
        }

        DrawStyle.NEON -> {
            val glow = android.graphics.Paint(paint).apply {
                strokeWidth = paint.strokeWidth * 3.2f
                color = androidInk(stroke, 0.35f)
            }
            canvas.drawPath(path, glow)
            canvas.drawPath(path, paint)
        }

        DrawStyle.STAMPS -> {
            val faces = arrayOf("😣", "🙂", "😒", "😄")
            val text = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                textSize = (paint.strokeWidth * 2.2f).coerceAtLeast(18f)
                textAlign = android.graphics.Paint.Align.CENTER
            }
            var index = 0
            var previous: StrokePoint? = null
            var walked = 0f
            stroke.points.forEach { point ->
                previous?.let {
                    walked += hypot((point.x - it.x) * width, (point.y - it.y) * height)
                }
                if (previous == null || walked >= text.textSize * 1.15f) {
                    canvas.drawText(
                        faces[index % faces.size],
                        point.x * width,
                        point.y * height,
                        text
                    )
                    index++
                    walked = 0f
                }
                previous = point
            }
        }

        DrawStyle.RECT -> {
            val box = bounds(stroke.points)
            canvas.drawRoundRect(
                box[0] * width, box[1] * height, box[2] * width, box[3] * height,
                8f, 8f, paint,
            )
        }

        DrawStyle.OVAL -> {
            val box = bounds(stroke.points)
            canvas.drawOval(box[0] * width, box[1] * height, box[2] * width, box[3] * height, paint)
        }

        DrawStyle.LINE, DrawStyle.ARROW -> {
            val a = stroke.points.first()
            val b = stroke.points.last()
            canvas.drawLine(a.x * width, a.y * height, b.x * width, b.y * height, paint)
            if (stroke.style == DrawStyle.ARROW) {
                val angle = atan2((b.y - a.y) * height, (b.x - a.x) * width)
                val head = paint.strokeWidth * 4f + 12f
                val fill = android.graphics.Paint(paint)
                    .apply { style = android.graphics.Paint.Style.FILL }
                val arrow = android.graphics.Path().apply {
                    moveTo(b.x * width, b.y * height)
                    lineTo(
                        b.x * width - head * cos(angle - 0.45f),
                        b.y * height - head * sin(angle - 0.45f)
                    )
                    lineTo(
                        b.x * width - head * cos(angle + 0.45f),
                        b.y * height - head * sin(angle + 0.45f)
                    )
                    close()
                }
                canvas.drawPath(arrow, fill)
            }
        }

        else -> canvas.drawPath(path, paint)
    }
}

private fun androidPath(stroke: DrawStroke, width: Int, height: Int): android.graphics.Path {
    val path = android.graphics.Path()
    val first = stroke.points.first()
    path.moveTo(first.x * width, first.y * height)
    stroke.points.drop(1).forEach { path.lineTo(it.x * width, it.y * height) }
    return path
}

private fun DrawScope.drawStyleIcon(style: DrawStyle) {
    val color = Color.White
    when (style) {
        DrawStyle.ERASER -> drawSvg(EraserPath)
        DrawStyle.PEN -> markerSquiggle(Color.White, 4.2.dp.toPx())
        DrawStyle.DOTTED -> markerSquiggle(
            Color.White,
            4.2.dp.toPx(),
            PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 7.dp.toPx())),
        )

        DrawStyle.NEON -> {
            markerSquiggle(Color(0xFFB388FF).copy(alpha = 0.55f), 9.dp.toPx())
            markerSquiggle(Color(0xFFE1BEE7), 5.2.dp.toPx())
            markerSquiggle(Color.White, 2.2.dp.toPx())
        }

        DrawStyle.STAMPS -> drawFaces()
        DrawStyle.LINE -> drawLine(
            color,
            Offset(size.width * 0.2f, size.height * 0.75f),
            Offset(size.width * 0.8f, size.height * 0.25f),
            2.dp.toPx(),
            StrokeCap.Round
        )

        DrawStyle.RECT -> drawRoundRect(
            color,
            Offset(size.width * 0.18f, size.height * 0.22f),
            Size(size.width * 0.64f, size.height * 0.56f),
            CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(2.dp.toPx())
        )

        DrawStyle.OVAL -> drawOval(
            color,
            Offset(size.width * 0.18f, size.height * 0.18f),
            Size(size.width * 0.64f, size.height * 0.64f),
            style = Stroke(2.dp.toPx())
        )

        DrawStyle.ARROW -> {
            val start = Offset(size.width * 0.22f, size.height * 0.22f)
            val end = Offset(size.width * 0.78f, size.height * 0.78f)
            drawLine(color, start, end, 2.dp.toPx(), StrokeCap.Round)
            val angle = atan2(end.y - start.y, end.x - start.x)
            val head = 8.dp.toPx()
            val path = Path().apply {
                moveTo(end.x, end.y)
                lineTo(end.x - head * cos(angle - 0.5f), end.y - head * sin(angle - 0.5f))
                lineTo(end.x - head * cos(angle + 0.5f), end.y - head * sin(angle + 0.5f))
                close()
            }
            drawPath(path, color)
        }
    }
}

private fun dashIntervals(width: Float) = floatArrayOf(width * 1.15f, width * 2.6f)

private fun DrawScope.markerSquiggle(color: Color, width: Float, effect: PathEffect? = null) {
    val path = Path().apply {
        moveTo(size.width * 0.08f, size.height * 0.72f)
        cubicTo(
            size.width * 0.22f, size.height * 0.28f,
            size.width * 0.40f, size.height * 0.16f,
            size.width * 0.56f, size.height * 0.50f,
        )
        cubicTo(
            size.width * 0.68f, size.height * 0.74f,
            size.width * 0.70f, size.height * 0.82f,
            size.width * 0.94f, size.height * 0.34f,
        )
    }
    drawPath(
        path,
        color,
        style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round, pathEffect = effect)
    )
}

private fun DrawScope.drawFaces() {
    val colors = listOf(Color(0xFF81C784), Color(0xFFB39DDB), Color(0xFFFFF176), Color(0xFFF5F5F5))
    val spots = listOf(0.28f to 0.32f, 0.72f to 0.32f, 0.28f to 0.72f, 0.72f to 0.72f)
    spots.forEachIndexed { index, (x, y) ->
        drawCircle(
            colors[index],
            size.minDimension * 0.16f,
            Offset(size.width * x, size.height * y)
        )
    }
}

private const val DropperPath =
    "M20.71,5.63L18.37,3.29c-0.39,-0.39 -1.02,-0.39 -1.41,0l-3.12,3.12 -1.93,-1.91 -1.41,1.41 1.93,1.93L4,16.27V20h3.73l10.32,-10.32 1.92,1.93 1.41,-1.41 -1.92,-1.92 3.12,-3.12c0.4,-0.39 0.4,-1.03 0.01,-1.42zM6.92,18H6v-0.92l7.06,-7.06 0.94,0.94L6.92,18z"

private const val EraserPath =
    "M17.25,18H22v2h-6.75zm-12.5,2l-2.125,-2.125q-0.575,-0.575 -0.587,-1.425T2.6,15l11,-11.4q0.575,-0.6 1.413,-0.6t1.412,0.575L21.4,8.55q0.575,0.575 0.575,1.425T21.4,11.4L13,20z"

private fun DrawScope.drawSvg(data: String) {
    val path = PathParser().parsePathString(data).toPath()
    val s = size.minDimension / 24f
    translate((size.width - 24f * s) / 2f, (size.height - 24f * s) / 2f) {
        scale(s, s, pivot = Offset.Zero) {
            drawPath(path, Color.White)
        }
    }
}

private fun DrawScope.drawBrushMark(color: Color) {
    drawRoundRect(
        color,
        Offset(size.width * 0.12f, size.height * 0.38f),
        Size(size.width * 0.76f, size.height * 0.24f),
        CornerRadius(size.height, size.height)
    )
}

private fun DrawScope.drawFadeMark(color: Color) {
    drawCircle(color, size.minDimension * 0.36f)
    drawCircle(Ink, size.minDimension * 0.36f, Offset(size.width * 0.62f, size.height * 0.5f))
    drawCircle(
        color.copy(alpha = 0.35f),
        size.minDimension * 0.28f,
        Offset(size.width * 0.68f, size.height * 0.5f)
    )
}

private fun inkColor(stroke: DrawStroke): Color {
    val rgb = stroke.color.toInt() and 0xFFFFFF
    val alpha = (stroke.opacity.coerceIn(0f, 1f) * 255).toInt()
    return Color(alpha shl 24 or rgb)
}

private fun androidInk(stroke: DrawStroke, extra: Float = 1f): Int {
    val rgb = stroke.color.toInt() and 0xFFFFFF
    val alpha = (stroke.opacity.coerceIn(0f, 1f) * extra * 255).toInt().coerceIn(0, 255)
    return alpha shl 24 or rgb
}

private fun bounds(points: List<StrokePoint>): FloatArray {
    var left = Float.POSITIVE_INFINITY
    var top = Float.POSITIVE_INFINITY
    var right = Float.NEGATIVE_INFINITY
    var bottom = Float.NEGATIVE_INFINITY
    points.forEach {
        left = min(left, it.x)
        top = min(top, it.y)
        right = maxOf(right, it.x)
        bottom = maxOf(bottom, it.y)
    }
    return floatArrayOf(left, top, right, bottom)
}

private fun distanceToSegment(point: StrokePoint, a: StrokePoint, b: StrokePoint): Float {
    val dx = b.x - a.x
    val dy = b.y - a.y
    val length = dx * dx + dy * dy
    val t =
        if (length == 0f) 0f else (((point.x - a.x) * dx + (point.y - a.y) * dy) / length).coerceIn(
            0f,
            1f
        )
    val x = a.x + dx * t - point.x
    val y = a.y + dy * t - point.y
    return hypot(x, y)
}

