package com.asinosoft.photogallery.ui.editor

import android.graphics.LinearGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.zIndex
import com.asinosoft.photogallery.R
import java.time.LocalDate
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint as AndroidPaint

private enum class TextPage { KEYBOARD, TEMPLATES, STYLE }

private data class TextTemplate(val id: String, val sample: String)

private val textTemplates = listOf(
    TextTemplate("dash", ""),
    TextTemplate("bubble", "Have a nice day!"),
    TextTemplate("round", "Enjoy yourself!"),
    TextTemplate("rect", "Wonderful!"),
    TextTemplate("oval", "Best Wishes!"),
    TextTemplate("tag", "GOOD LUCK!"),
    TextTemplate("frame", "Beautiful"),
    TextTemplate("cloud", "WOW"),
    TextTemplate("boom", "BOOM"),
    TextTemplate("floral", "Happy Children's Day"),
    TextTemplate("xmas", "Merry Christmas"),
    TextTemplate("love", "Valentine's Day"),
    TextTemplate("date", "Have a nice day"),
    TextTemplate("paint", "paint"),
    TextTemplate("makeup", "Make up"),
)

private val Chip = Color(0xFF2A2A2A)
private val Amber = Color(0xFFFFD54F)
private val ChipHeight = 64.dp

private val textStyles = listOf(
    "badge", "glitterPink", "gold", "rainbow", "neon", "scriptGlow",
    "aqua", "sunset", "glitterPurple", "outlineBlue", "blockBlue", "split",
    "blocks", "paint", "brush",
)

private data class EditorFont(
    val id: String,
    val label: String,
    val family: String,
    val style: Int,
    val spacing: Float
)

private val editorFonts = listOf(
    EditorFont("normal", "Normal", "sans-serif-black", Typeface.BOLD, 0f),
    EditorFont("concrete", "Concrete", "serif", Typeface.BOLD, 0.04f),
    EditorFont("double", "DOUBLE FEATURE", "serif", Typeface.BOLD, 0.02f),
    EditorFont("leira", "LEIRA LITE", "sans-serif-thin", Typeface.NORMAL, 0.18f),
    EditorFont("butter", "butter sans", "casual", Typeface.NORMAL, 0.02f),
    EditorFont("addict", "Addict Script", "cursive", Typeface.ITALIC, 0f),
    EditorFont("morganite", "Morganite", "sans-serif-condensed", Typeface.BOLD, 0.08f),
    EditorFont("aideep", "AiDeep", "sans-serif-medium", Typeface.NORMAL, 0.12f),
    EditorFont("kaushan", "KaushanScript", "cursive", Typeface.NORMAL, 0f),
    EditorFont("giant", "GIANTFONT", "sans-serif-black", Typeface.BOLD, 0.06f),
    EditorFont("brusher", "Brusher", "cursive", Typeface.BOLD, 0f),
    EditorFont("kavi", "Kavivanar", "casual", Typeface.ITALIC, 0.04f),
    EditorFont("floane", "Floane", "sans-serif-light", Typeface.NORMAL, 0.06f),
    EditorFont("jam", "Jam Mono", "monospace", Typeface.BOLD, 0.08f),
    EditorFont("aurora", "Cute Aurora", "sans-serif-light", Typeface.NORMAL, 0.04f),
    EditorFont("repair", "Repair", "serif", Typeface.BOLD, 0.02f),
    EditorFont("baby", "baby ground", "casual", Typeface.NORMAL, 0.01f),
    EditorFont("aemstel", "AEMSTEL LINE", "sans-serif-condensed", Typeface.NORMAL, 0.14f),
    EditorFont("remem", "Remem", "sans-serif-light", Typeface.NORMAL, 0.16f),
)

private fun fontOf(id: String) = editorFonts.find { it.id == id } ?: editorFonts.first()

private fun typefaceOf(id: String): Typeface {
    val font = fontOf(id)
    return Typeface.create(font.family, font.style)
}

private fun templateInk(templateId: String, color: Long): Long = when {
    color != 0xFFFFFFFFL -> color
    templateId == "cloud" -> 0xFFFBC02DL
    templateId == "boom" -> 0xFFE53935L
    templateId == "floral" || templateId == "makeup" -> 0xFFEC407AL
    templateId == "xmas" -> 0xFF1B5E20L
    templateId == "date" || templateId == "paint" || templateId == "plain" -> 0xFFFFFFFFL
    else -> 0xFF212121L
}

private fun templateFont(templateId: String): String = when (templateId) {
    "floral", "xmas", "love", "paint", "makeup" -> "kaushan"
    else -> "normal"
}

@Composable
fun TextPanel(
    selected: TextOverlay?,
    drawSettings: DrawSettings,
    onDraw: (DrawSettings) -> Unit,
    onEdit: (text: String?, color: Long?, fontId: String?, styleId: String?, templateId: String?, outlined: Boolean?, live: Boolean) -> Unit,
    onKeyboard: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    buttonSize: Dp = 46.dp,
    chipSize: Dp = ChipHeight,
) {
    var page by remember { mutableStateOf(TextPage.TEMPLATES) }
    var colors by remember { mutableStateOf(false) }
    val color =
        if (drawSettings.picking) drawSettings.color else selected?.color ?: drawSettings.color
    val outlined = selected?.outlined == true
    Column(modifier
        .fillMaxWidth()
        .padding(horizontal = 8.dp)) {
        TextToolRow(
            buttonSize = buttonSize,
            page = page,
            outlined = outlined,
            onPage = {
                colors = false
                page = it
                if (it == TextPage.KEYBOARD) onKeyboard()
            },
            onOutline = { onEdit(null, null, null, null, null, !outlined, false) },
            onDone = onDone,
            colorSlot = {
                Box {
                    Box(
                        Modifier
                            .size(buttonSize)
                            .clip(RoundedCornerShape(buttonSize * 0.26f))
                            .background(Color(0xFF2A2A2A))
                            .clickable { colors = !colors },
                        contentAlignment = Alignment.Center,
                    ) {
                        Box(
                            Modifier
                                .fillMaxSize(0.48f)
                                .clip(RoundedCornerShape(buttonSize * 0.13f))
                                .background(Color(color)),
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
                                    .background(Color(0xFF2A2A2A))
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
                                            onDraw(drawSettings.copy(picking = true))
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Canvas(Modifier.size(22.dp)) { drawDropperIcon() }
                                }
                                drawPalette.forEach { swatch ->
                                    val on = swatch == color && !drawSettings.picking
                                    Box(
                                        Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(Color(swatch))
                                            .then(
                                                if (on) Modifier.border(
                                                    2.dp,
                                                    Color(0xFFFFD54F),
                                                    RoundedCornerShape(10.dp)
                                                )
                                                else Modifier
                                            )
                                            .clickable {
                                                onDraw(
                                                    drawSettings.copy(
                                                        color = swatch,
                                                        picking = false
                                                    )
                                                )
                                                onEdit(
                                                    null,
                                                    swatch,
                                                    null,
                                                    "solid",
                                                    null,
                                                    null,
                                                    false
                                                )
                                                colors = false
                                            },
                                    )
                                }
                            }
                        }
                    }
                }
            },
        )
        if (page != TextPage.KEYBOARD) Spacer(Modifier.height(8.dp))
        when (page) {
            TextPage.KEYBOARD -> Unit
            TextPage.TEMPLATES -> TemplateStrip(selected?.templateId, chipSize) { template ->
                val ink = templateInk(template.id, 0xFFFFFFFF)
                onEdit(null, ink, templateFont(template.id), "solid", template.id, null, false)
            }

            TextPage.STYLE -> StyleFontStrip(
                styleId = selected?.styleId,
                fontId = selected?.fontId,
                chipSize = chipSize,
                onStyle = { onEdit(null, null, null, it, null, null, false) },
                onFont = { onEdit(null, null, it, null, null, null, false) },
            )
        }
    }
}

@Composable
private fun TextToolRow(
    page: TextPage,
    buttonSize: Dp,
    outlined: Boolean,
    onPage: (TextPage) -> Unit,
    onOutline: () -> Unit,
    onDone: () -> Unit,
    colorSlot: @Composable () -> Unit,
) {
    val corner = buttonSize * 0.26f
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ToolGlyph(page == TextPage.KEYBOARD, KeyboardIcon, buttonSize) { onPage(TextPage.KEYBOARD) }
        ToolGlyph(page == TextPage.TEMPLATES, BubbleIcon, buttonSize) { onPage(TextPage.TEMPLATES) }
        Box(
            Modifier
                .size(buttonSize)
                .clip(RoundedCornerShape(corner))
                .background(Chip)
                .clickable { onPage(TextPage.STYLE) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "A",
                color = Color.White,
                fontSize = (22f * buttonSize.value / 46f).sp,
                fontWeight = FontWeight.Bold,
            )
            if (page == TextPage.STYLE) AmberRing(corner)
        }
        colorSlot()
        ToolGlyph(outlined, PenIcon, buttonSize, onOutline)
        ToolGlyph(false, CheckIcon, buttonSize, onDone)
    }
}

@Composable
private fun AmberRing(corner: androidx.compose.ui.unit.Dp = 12.dp) {
    Canvas(Modifier.fillMaxSize()) {
        drawRoundRect(
            Amber,
            style = Stroke(width = 2.dp.toPx()),
            cornerRadius = CornerRadius(corner.toPx(), corner.toPx()),
        )
    }
}

@Composable
private fun ToolGlyph(selected: Boolean, icon: Int, size: Dp, onClick: () -> Unit) {
    val corner = size * 0.26f
    Box(
        Modifier
            .size(size)
            .clip(RoundedCornerShape(corner))
            .background(Chip)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize(0.52f)) { textToolIcon(icon) }
        if (selected) AmberRing(corner)
    }
}

private const val KeyboardIcon = 0
private const val BubbleIcon = 1
private const val PenIcon = 2
private const val CheckIcon = 3

private fun androidx.compose.ui.graphics.drawscope.DrawScope.textToolIcon(icon: Int) {
    val color = Color.White
    val stroke = Stroke(width = 1.6.dp.toPx())
    when (icon) {
        KeyboardIcon -> {
            drawRoundRect(
                color,
                Offset(size.width * 0.08f, size.height * 0.16f),
                Size(size.width * 0.84f, size.height * 0.68f),
                CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = stroke
            )
            val keys = listOf(
                0.28f to 0.36f,
                0.5f to 0.36f,
                0.72f to 0.36f,
                0.28f to 0.56f,
                0.5f to 0.56f,
                0.72f to 0.56f
            )
            keys.forEach { (x, y) ->
                drawCircle(
                    color,
                    1.4.dp.toPx(),
                    Offset(size.width * x, size.height * y)
                )
            }
        }

        BubbleIcon -> {
            drawCircle(
                color,
                size.minDimension * 0.28f,
                Offset(size.width * 0.46f, size.height * 0.40f),
                style = stroke
            )
            val tail = Path().apply {
                moveTo(size.width * 0.34f, size.height * 0.58f)
                lineTo(size.width * 0.22f, size.height * 0.78f)
                lineTo(size.width * 0.48f, size.height * 0.62f)
                close()
            }
            drawPath(tail, color)
            listOf(0.34f, 0.46f, 0.58f).forEach { x ->
                drawCircle(color, 1.3.dp.toPx(), Offset(size.width * x, size.height * 0.40f))
            }
        }

        PenIcon -> {
            drawRoundRect(
                color,
                Offset(size.width * 0.12f, size.height * 0.12f),
                Size(size.width * 0.76f, size.height * 0.76f),
                CornerRadius(4.dp.toPx(), 4.dp.toPx()),
                style = stroke
            )
            drawLine(
                color,
                Offset(size.width * 0.32f, size.height * 0.68f),
                Offset(size.width * 0.70f, size.height * 0.30f),
                1.6.dp.toPx()
            )
            drawLine(
                color,
                Offset(size.width * 0.62f, size.height * 0.28f),
                Offset(size.width * 0.74f, size.height * 0.36f),
                1.6.dp.toPx()
            )
        }

        CheckIcon -> {
            drawLine(
                color,
                Offset(size.width * 0.18f, size.height * 0.52f),
                Offset(size.width * 0.40f, size.height * 0.74f),
                2.2.dp.toPx()
            )
            drawLine(
                color,
                Offset(size.width * 0.40f, size.height * 0.74f),
                Offset(size.width * 0.82f, size.height * 0.28f),
                2.2.dp.toPx()
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawDropperIcon() {
    val path = PathParser().parsePathString(
        "M20.71,5.63L18.37,3.29c-0.39,-0.39 -1.02,-0.39 -1.41,0l-3.12,3.12 -1.93,-1.91 -1.41,1.41 1.93,1.93L4,16.27V20h3.73l10.32,-10.32 1.92,1.93 1.41,-1.41 -1.92,-1.92 3.12,-3.12c0.4,-0.39 0.4,-1.03 0.01,-1.42zM6.92,18H6v-0.92l7.06,-7.06 0.94,0.94L6.92,18z",
    ).toPath()
    val s = size.minDimension / 24f
    translate((size.width - 24f * s) / 2f, (size.height - 24f * s) / 2f) {
        scale(s, s, pivot = Offset.Zero) { drawPath(path, Color.White) }
    }
}

@Composable
private fun TemplateStrip(selectedId: String?, chipSize: Dp, onPick: (TextTemplate) -> Unit) {
    val first = textTemplates.filterIndexed { index, _ -> index < 8 }
    val second = textTemplates.filterIndexed { index, _ -> index >= 8 }
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        first.forEach { card -> TemplateCard(card, chipSize, card.id == selectedId, onPick) }
        second.forEach { card -> TemplateCard(card, chipSize, card.id == selectedId, onPick) }
    }
}

@Composable
private fun TemplateCard(
    template: TextTemplate,
    chipSize: Dp,
    selected: Boolean,
    onPick: (TextTemplate) -> Unit,
) {
    Box(
        Modifier
            .size(chipSize)
            .border(
                2.dp,
                if (selected) Amber else Color.Transparent,
                RoundedCornerShape(14.dp),
            )
            .clip(RoundedCornerShape(14.dp))
            .background(Chip)
            .clickable { onPick(template) }
            .padding(6.dp),
        contentAlignment = Alignment.Center,
    ) {
        TemplateArtwork(
            template.id,
            template.sample,
            templateInk(template.id, 0xFFFFFFFF),
            templateFont(template.id),
            "solid",
            false,
            8.sp,
        )
    }
}

private fun fontForStyle(style: String) = when (style) {
    "scriptGlow", "brush", "outlineBlue" -> "kaushan"
    "blockBlue", "blocks" -> "normal"
    else -> "normal"
}

@Composable
private fun StyleFontStrip(
    styleId: String?,
    fontId: String?,
    chipSize: Dp,
    onStyle: (String) -> Unit,
    onFont: (String) -> Unit,
) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        textStyles.forEach { style ->
            Box(
                Modifier
                    .size(chipSize)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Chip)
                    .clickable { onStyle(style) },
                contentAlignment = Alignment.Center,
            ) {
                StyledLine("A", style, fontForStyle(style), 0xFFFFFFFF, 26.sp)
                if (style == styleId) AmberRing(14.dp)
            }
        }
        editorFonts.forEach { font ->
            Box(
                Modifier
                    .width(132.dp)
                    .height(chipSize)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Chip)
                    .clickable { onFont(font.id) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    font.label,
                    modifier = Modifier.padding(horizontal = 8.dp),
                    color = Color(0xFFE0E0E0),
                    fontSize = if (font.label.length > 12) 13.sp else 16.sp,
                    fontFamily = FontFamily(typefaceOf(font.id)),
                    letterSpacing = (font.spacing * 8f).sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (font.id == fontId) AmberRing(14.dp)
            }
        }
    }
}

@Composable
fun TemplateArtwork(
    templateId: String,
    text: String,
    color: Long,
    fontId: String,
    styleId: String,
    outlined: Boolean,
    fontSize: TextUnit,
    editing: Boolean = false,
    onTextChange: (String) -> Unit = {},
    editFocus: FocusRequester? = null,
) {
    Box(
        Modifier
            .drawBehind { drawTemplate(templateId) }
            .padding(templatePadding(templateId))
            .then(if (templateId == "dash") Modifier.defaultMinSize(52.dp, 28.dp) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            if (templateId == "date") {
                val today = LocalDate.now()
                Text(
                    "${today.year}/${today.monthValue}/${today.dayOfMonth}",
                    color = Color.White,
                    fontSize = fontSize * 0.78f,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            Box(Modifier.graphicsLayer { if (templateId == "boom") rotationZ = -8f }) {
                if (editing && editFocus != null) {
                    var lineWidth by remember { mutableIntStateOf(0) }
                    BasicTextField(
                        value = text,
                        onValueChange = onTextChange,
                        textStyle = editTextStyle(styleId, fontId, color, fontSize),
                        cursorBrush = SolidColor(Color(0xFFFFD54F)),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.None),
                        onTextLayout = { result ->
                            var widest = 0f
                            for (line in 0 until result.lineCount) {
                                widest = maxOf(
                                    widest,
                                    result.getLineRight(line) - result.getLineLeft(line)
                                )
                            }
                            val next = widest.roundToInt()
                            if (next != lineWidth) lineWidth = next
                        },
                        modifier = Modifier
                            .clipToBounds()
                            .focusRequester(editFocus)
                            .layout { measurable, constraints ->
                                val placeable = measurable.measure(
                                    constraints.copy(minWidth = 0, maxWidth = 10_000)
                                )
                                val minWidth = 18.dp.roundToPx()
                                val width = (lineWidth + 2.dp.roundToPx())
                                    .coerceAtLeast(minWidth)
                                    .coerceIn(constraints.minWidth, constraints.maxWidth)
                                val height = placeable.height.coerceIn(
                                    constraints.minHeight,
                                    constraints.maxHeight
                                )
                                layout(width, height) { placeable.place(0, 0) }
                            },
                    )
                } else {
                    StyledLine(
                        text,
                        styleId,
                        fontId,
                        color,
                        fontSize,
                        outlined || templateId == "paint" || fontId == "giant" || fontId == "aemstel",
                    )
                }
            }
        }
    }
}

private fun templatePadding(id: String) = when (id) {
    "dash" -> PaddingValues(horizontal = 10.dp, vertical = 8.dp)
    "bubble" -> PaddingValues(start = 10.dp, top = 6.dp, end = 10.dp, bottom = 11.dp)
    "round" -> PaddingValues(horizontal = 12.dp, vertical = 10.dp)
    "rect" -> PaddingValues(start = 8.dp, top = 5.dp, end = 8.dp, bottom = 9.dp)
    "oval" -> PaddingValues(horizontal = 12.dp, vertical = 7.dp)
    "tag" -> PaddingValues(start = 8.dp, top = 5.dp, end = 14.dp, bottom = 5.dp)
    "frame" -> PaddingValues(start = 8.dp, top = 5.dp, end = 10.dp, bottom = 8.dp)
    "cloud", "boom" -> PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    "xmas" -> PaddingValues(horizontal = 10.dp, vertical = 7.dp)
    "date" -> PaddingValues(horizontal = 8.dp, vertical = 3.dp)
    "paint" -> PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    "makeup" -> PaddingValues(horizontal = 8.dp, vertical = 3.dp)
    else -> PaddingValues(0.dp)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawTemplate(id: String) {
    val w = size.width
    val h = size.height
    if (w < 2f || h < 2f) return
    val ink = Color(0xFF212121)
    val line = Stroke(width = 1.3.dp.toPx())
    when (id) {
        "dash" -> drawRoundRect(
            ink,
            Offset(w * 0.04f, h * 0.08f),
            Size(w * 0.92f, h * 0.84f),
            CornerRadius(3.dp.toPx(), 3.dp.toPx()),
            style = Stroke(
                1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 5f))
            ),
        )

        "bubble" -> speech(
            w * 0.02f,
            h * 0.04f,
            w * 0.96f,
            h * 0.72f,
            7.dp.toPx(),
            w * 0.18f,
            h * 0.96f,
            w * 0.16f,
            ink,
            line
        )

        "round" -> {
            drawOval(Color.White, Offset(w * 0.06f, h * 0.02f), Size(w * 0.88f, h * 0.78f))
            val tail = Path().apply {
                moveTo(w * 0.42f, h * 0.70f)
                lineTo(w * 0.40f, h * 0.98f)
                lineTo(w * 0.62f, h * 0.72f)
                close()
            }
            drawPath(tail, Color.White)
            drawOval(ink, Offset(w * 0.06f, h * 0.02f), Size(w * 0.88f, h * 0.78f), style = line)
            drawLine(ink, Offset(w * 0.42f, h * 0.74f), Offset(w * 0.40f, h * 0.98f), line.width)
            drawLine(ink, Offset(w * 0.40f, h * 0.98f), Offset(w * 0.60f, h * 0.76f), line.width)
        }

        "rect" -> speech(
            w * 0.02f,
            h * 0.08f,
            w * 0.98f,
            h * 0.74f,
            2.dp.toPx(),
            w * 0.16f,
            h * 0.98f,
            w * 0.10f,
            ink,
            line
        )

        "oval" -> {
            drawOval(Color.White, Offset(w * 0.02f, h * 0.12f), Size(w * 0.96f, h * 0.70f))
            val tail = Path().apply {
                moveTo(w * 0.78f, h * 0.70f)
                lineTo(w * 0.92f, h * 0.96f)
                lineTo(w * 0.70f, h * 0.72f)
                close()
            }
            drawPath(tail, Color.White)
            drawOval(ink, Offset(w * 0.02f, h * 0.12f), Size(w * 0.96f, h * 0.70f), style = line)
            drawLine(ink, Offset(w * 0.80f, h * 0.74f), Offset(w * 0.92f, h * 0.96f), line.width)
            drawLine(ink, Offset(w * 0.92f, h * 0.96f), Offset(w * 0.72f, h * 0.76f), line.width)
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
            drawPath(path, Color.White)
            drawPath(path, ink, style = line)
        }

        "frame" -> {
            drawRoundRect(
                Color(0xFF64B5F6),
                Offset(w * 0.08f, h * 0.20f),
                Size(w * 0.88f, h * 0.70f),
                CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                style = line
            )
            drawRoundRect(Color.White, Offset(w * 0.02f, h * 0.08f), Size(w * 0.86f, h * 0.70f))
            drawRoundRect(
                ink,
                Offset(w * 0.02f, h * 0.08f),
                Size(w * 0.86f, h * 0.70f),
                CornerRadius(1.dp.toPx(), 1.dp.toPx()),
                style = Stroke(1.6.dp.toPx())
            )
        }

        "cloud" -> {
            val path = cloudPath(w, h)
            drawPath(path, Color.White)
            drawPath(path, ink, style = line)
        }

        "boom" -> {
            val path = burstPath(w, h)
            drawPath(path, Color.White)
            drawPath(path, ink, style = line)
        }

        "floral" -> {
            flower(Offset(w * 0.10f, h * 0.22f), minOf(w, h) * 0.16f, Color(0xFFF48FB1))
            flower(Offset(w * 0.24f, h * 0.12f), minOf(w, h) * 0.11f, Color(0xFFF8BBD0))
            flower(Offset(w * 0.88f, h * 0.78f), minOf(w, h) * 0.14f, Color(0xFFCE93D8))
            flower(Offset(w * 0.74f, h * 0.90f), minOf(w, h) * 0.09f, Color(0xFFF48FB1))
        }

        "xmas" -> {
            drawRoundRect(
                Color(0xFF2E7D32),
                Offset(w * 0.04f, h * 0.08f),
                Size(w * 0.92f, h * 0.84f),
                CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = Stroke(1.4.dp.toPx())
            )
            drawRoundRect(
                Color(0xFF2E7D32),
                Offset(w * 0.09f, h * 0.16f),
                Size(w * 0.82f, h * 0.68f),
                CornerRadius(2.dp.toPx(), 2.dp.toPx()),
                style = Stroke(
                    1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 3f))
                ),
            )
            holly(Offset(w * 0.12f, h * 0.16f))
            holly(Offset(w * 0.88f, h * 0.84f))
        }

        "love" -> {
            val s = minOf(w, h) * 0.16f
            heart(Offset(w * 0.08f, h * 0.18f), s, Color(0xFFE53935))
            heart(Offset(w * 0.92f, h * 0.16f), s * 0.8f, Color(0xFFEC407A))
            heart(Offset(w * 0.10f, h * 0.86f), s * 0.7f, Color(0xFFEC407A))
            heart(Offset(w * 0.90f, h * 0.84f), s, Color(0xFFE53935))
        }

        "date" -> drawRoundRect(
            Color.Black,
            Offset.Zero,
            Size(w, h),
            CornerRadius(2.dp.toPx(), 2.dp.toPx())
        )

        "paint" -> {
            drawOval(
                Color.White,
                Offset(w * 0.08f, h * 0.10f),
                Size(w * 0.80f, h * 0.78f),
                style = Stroke(1.3.dp.toPx())
            )
            drawOval(
                Color.White,
                Offset(w * 0.16f, h * 0.16f),
                Size(w * 0.76f, h * 0.70f),
                style = Stroke(1.1.dp.toPx())
            )
        }

        "makeup" -> drawRoundRect(
            Color(0xFFF48FB1),
            Offset(0f, h * 0.28f),
            Size(w, h * 0.46f),
            CornerRadius(h, h),
        )

        else -> Unit
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.speech(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
    tailX: Float,
    tailY: Float,
    tailWidth: Float,
    ink: Color,
    line: Stroke,
) {
    drawRoundRect(
        Color.White,
        Offset(left, top),
        Size(right - left, bottom - top),
        CornerRadius(radius, radius)
    )
    val tail = Path().apply {
        moveTo(left + tailWidth, bottom - 1f)
        lineTo(tailX, tailY)
        lineTo(left + tailWidth * 2.4f, bottom - 1f)
        close()
    }
    drawPath(tail, Color.White)
    drawRoundRect(
        ink,
        Offset(left, top),
        Size(right - left, bottom - top),
        CornerRadius(radius, radius),
        style = line
    )
    drawLine(ink, Offset(left + tailWidth, bottom), Offset(tailX, tailY), line.width)
    drawLine(ink, Offset(tailX, tailY), Offset(left + tailWidth * 2.4f, bottom), line.width)
}

private fun cloudPath(w: Float, h: Float) = Path().apply {
    moveTo(w * 0.22f, h * 0.62f)
    cubicTo(w * 0.04f, h * 0.64f, w * 0.04f, h * 0.36f, w * 0.26f, h * 0.34f)
    cubicTo(w * 0.30f, h * 0.12f, w * 0.55f, h * 0.08f, w * 0.60f, h * 0.32f)
    cubicTo(w * 0.82f, h * 0.20f, w * 0.98f, h * 0.40f, w * 0.84f, h * 0.58f)
    cubicTo(w * 0.98f, h * 0.74f, w * 0.78f, h * 0.92f, w * 0.56f, h * 0.78f)
    cubicTo(w * 0.46f, h * 0.94f, w * 0.24f, h * 0.90f, w * 0.22f, h * 0.70f)
    close()
}

private fun burstPath(w: Float, h: Float) = Path().apply {
    val cx = w * 0.50f
    val cy = h * 0.50f
    val spikes = 14
    for (i in 0 until spikes) {
        val angle = -Math.PI / 2.0 + i * 2.0 * Math.PI / spikes
        val outer = i % 2 == 0
        val rx = w * if (outer) 0.50f else 0.30f
        val ry = h * if (outer) 0.48f else 0.28f
        val x = cx + kotlin.math.cos(angle).toFloat() * rx
        val y = cy + kotlin.math.sin(angle).toFloat() * ry
        if (i == 0) moveTo(x, y) else lineTo(x, y)
    }
    close()
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.flower(
    at: Offset,
    radius: Float,
    color: Color
) {
    val petals = 5
    for (i in 0 until petals) {
        val angle = -Math.PI / 2.0 + i * 2.0 * Math.PI / petals
        val x = at.x + kotlin.math.cos(angle).toFloat() * radius * 0.7f
        val y = at.y + kotlin.math.sin(angle).toFloat() * radius * 0.7f
        drawCircle(color, radius * 0.55f, Offset(x, y))
    }
    drawCircle(Color(0xFFFFF176), radius * 0.38f, at)
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.holly(at: Offset) {
    drawCircle(Color(0xFF2E7D32), 3.2.dp.toPx(), Offset(at.x - 3.dp.toPx(), at.y))
    drawCircle(Color(0xFF388E3C), 3.2.dp.toPx(), Offset(at.x + 2.dp.toPx(), at.y - 1.dp.toPx()))
    drawCircle(Color(0xFFE53935), 1.8.dp.toPx(), Offset(at.x, at.y + 2.dp.toPx()))
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.heart(
    at: Offset,
    sizePx: Float,
    color: Color
) {
    val path = Path().apply {
        val s = sizePx
        moveTo(at.x, at.y + s * 0.35f)
        cubicTo(
            at.x - s,
            at.y - s * 0.35f,
            at.x - s * 0.35f,
            at.y - s * 0.7f,
            at.x,
            at.y - s * 0.15f
        )
        cubicTo(
            at.x + s * 0.35f,
            at.y - s * 0.7f,
            at.x + s,
            at.y - s * 0.35f,
            at.x,
            at.y + s * 0.35f
        )
        close()
    }
    drawPath(path, color)
}

private fun editTextStyle(
    styleId: String,
    fontId: String,
    color: Long,
    fontSize: TextUnit,
): TextStyle {
    val font = fontOf(fontId)
    val family = FontFamily(typefaceOf(fontId))
    val brush = styleBrush(styleId)
    val weight = if (font.style == Typeface.BOLD) FontWeight.Bold else FontWeight.Normal
    // em как у Paint.letterSpacing — совпадение с JPEG.
    val spacing = font.spacing.em
    val shadow = styleShadow(styleId)
    val platform = PlatformTextStyle(includeFontPadding = false)
    return if (brush != null) {
        TextStyle(
            brush = brush,
            fontSize = fontSize,
            fontFamily = family,
            fontWeight = weight,
            letterSpacing = spacing,
            textAlign = TextAlign.Start,
            shadow = shadow,
            platformStyle = platform,
        )
    } else {
        TextStyle(
            color = Color(if (styleId == "solid") color else styleColor(styleId, color)),
            fontSize = fontSize,
            fontFamily = family,
            fontWeight = weight,
            letterSpacing = spacing,
            textAlign = TextAlign.Start,
            shadow = shadow,
            platformStyle = platform,
        )
    }
}

@Composable
private fun StyledLine(
    text: String,
    styleId: String,
    fontId: String,
    color: Long,
    fontSize: TextUnit,
    outlined: Boolean = false,
) {
    if (text.isEmpty()) return
    val font = fontOf(fontId)
    val family = FontFamily(typefaceOf(fontId))
    val brush = styleBrush(styleId)
    val weight = if (font.style == Typeface.BOLD) FontWeight.Bold else FontWeight.Normal
    val spacing = font.spacing.em
    val shadow = styleShadow(styleId)
    val platform = PlatformTextStyle(includeFontPadding = false)
    val base = if (brush != null) {
        TextStyle(
            brush = brush,
            fontSize = fontSize,
            fontFamily = family,
            fontWeight = weight,
            letterSpacing = spacing,
            textAlign = TextAlign.Center,
            shadow = shadow,
            platformStyle = platform,
        )
    } else {
        TextStyle(
            color = Color(if (styleId == "solid") color else styleColor(styleId, color)),
            fontSize = fontSize,
            fontFamily = family,
            fontWeight = weight,
            letterSpacing = spacing,
            textAlign = TextAlign.Center,
            shadow = shadow,
            platformStyle = platform,
        )
    }
    val strokeColor =
        Color(if (styleId == "outlineBlue" || styleId == "blockBlue") 0xFF42A5F5 else color)
    Box(contentAlignment = Alignment.Center) {
        if (outlined || styleId == "outlineBlue" || styleId == "blockBlue") {
            Text(
                text,
                style = TextStyle(
                    color = strokeColor,
                    fontSize = fontSize,
                    fontFamily = family,
                    fontWeight = weight,
                    letterSpacing = spacing,
                    textAlign = TextAlign.Center,
                    drawStyle = Stroke(width = if (styleId == "blockBlue") 6f else 3f),
                    platformStyle = platform,
                ),
                textAlign = TextAlign.Center,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (styleId != "outlineBlue") {
            Text(
                text,
                style = base,
                textAlign = TextAlign.Center,
                maxLines = 12,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

private fun styleBrush(styleId: String): Brush? = when (styleId) {
    "gold" -> Brush.horizontalGradient(listOf(Color(0xFFFFF59D), Color(0xFFF9A825)))
    "rainbow" -> Brush.horizontalGradient(
        listOf(
            Color(0xFFE53935),
            Color(0xFFFDD835),
            Color(0xFF43A047),
            Color(0xFF1E88E5),
            Color(0xFF8E24AA)
        )
    )

    "aqua" -> Brush.horizontalGradient(listOf(Color(0xFF80DEEA), Color(0xFFFFF176)))
    "sunset" -> Brush.horizontalGradient(listOf(Color(0xFFFF8A65), Color(0xFFE53935)))
    "split" -> Brush.horizontalGradient(listOf(Color(0xFFEC407A), Color(0xFFFDD835)))
    "blocks" -> Brush.verticalGradient(
        listOf(
            Color(0xFFEC407A),
            Color(0xFF42A5F5),
            Color(0xFFFDD835)
        )
    )

    "paint" -> Brush.verticalGradient(
        listOf(
            Color(0xFF00E5FF),
            Color(0xFF76FF03),
            Color(0xFFFFEA00),
            Color(0xFFFF6D00)
        )
    )

    "brush" -> Brush.horizontalGradient(listOf(Color(0xFFFFA726), Color(0xFFE53935)))
    "glitterPink" -> Brush.linearGradient(
        listOf(
            Color(0xFFF8BBD0),
            Color(0xFFEC407A),
            Color(0xFFF8BBD0)
        )
    )

    "glitterPurple" -> Brush.linearGradient(
        listOf(
            Color(0xFFE1BEE7),
            Color(0xFF8E24AA),
            Color(0xFFE1BEE7)
        )
    )

    else -> null
}

private fun styleColor(styleId: String, color: Long): Long = when (styleId) {
    "badge" -> 0xFFFFFFFF
    "neon", "scriptGlow" -> 0xFFFFFFFF
    else -> color
}

private fun styleShadow(styleId: String): Shadow? = when (styleId) {
    "neon" -> Shadow(Color(0xFFE040FB), Offset.Zero, 16f)
    "scriptGlow" -> Shadow(Color(0xFFB388FF), Offset.Zero, 18f)
    "glitterPink" -> Shadow(Color(0xFFF48FB1), Offset(0f, 1f), 8f)
    "badge" -> Shadow(Color(0x66000000), Offset(0f, 1f), 2f)
    else -> null
}

@Composable
fun EditorTextsLayer(
    texts: List<TextOverlay>,
    contentFrame: OverlayContentFrame? = null,
    interactive: Boolean,
    typing: Boolean,
    selectedId: Long?,
    zoom: Float = 1f,
    onSelect: (Long?) -> Unit,
    onClear: () -> Unit,
    onTypeAt: (Float, Float) -> Unit,
    onTypeText: (Long) -> Unit,
    onTextChange: (String) -> Unit,
    onUpdate: (Long, Float?, Float?, Float?, Float?, Boolean) -> Unit,
    onDuplicate: (Long) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var layerCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { layerCoords = it },
    ) {
        val widthPx = constraints.maxWidth.toFloat().coerceAtLeast(1f)
        val heightPx = constraints.maxHeight.toFloat().coerceAtLeast(1f)
        val frameLeft = contentFrame?.left ?: 0f
        val frameTop = contentFrame?.top ?: 0f
        val frameW = contentFrame?.width?.takeIf { it > 1f } ?: widthPx
        val frameH = contentFrame?.height?.takeIf { it > 1f } ?: heightPx
        val sizeRefPx = frameW
        val density = LocalDensity.current
        val editFocus = remember { FocusRequester() }
        val keyboard = LocalSoftwareKeyboardController.current
        LaunchedEffect(typing, selectedId) {
            if (typing && selectedId != null) {
                withFrameNanos { }
                try {
                    editFocus.requestFocus()
                } catch (_: IllegalStateException) {
                }
                keyboard?.show()
            }
        }
        if (interactive) {
            Box(
                Modifier
                    .fillMaxSize()
                    .zIndex(0.5f)
                    .pointerInput(selectedId, typing, frameLeft, frameTop, frameW, frameH) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val start = down.position
                            val slop = viewConfiguration.touchSlop
                            while (true) {
                                val event = awaitPointerEvent()
                                // Не мешаем pinch-zoom / пан: второй палец или драг — выходим.
                                if (event.changes.count { it.pressed } >= 2) return@awaitEachGesture
                                val change = event.changes.firstOrNull { it.id == down.id }
                                    ?: return@awaitEachGesture
                                if (!change.pressed) {
                                    if (selectedId != null || typing) onClear()
                                    else {
                                        val nx = ((start.x - frameLeft) / frameW).coerceIn(0f, 1f)
                                        val ny = ((start.y - frameTop) / frameH).coerceIn(0f, 1f)
                                        onTypeAt(nx, ny)
                                    }
                                    return@awaitEachGesture
                                }
                                val delta = change.position - start
                                if (hypot(delta.x, delta.y) > slop) return@awaitEachGesture
                            }
                        }
                    },
            )
        }
        val frameWi = frameW.roundToInt().coerceAtLeast(1)
        val frameHi = frameH.roundToInt().coerceAtLeast(1)
        val onUpdateState by rememberUpdatedState(onUpdate)
        val z = zoom.coerceAtLeast(0.01f)
        val chromeStroke = with(density) { 1.5.dp.toPx() / z }
        val handleSizePx = with(density) { 22.dp.toPx() / z }
        val handleR = handleSizePx / 2f
        texts.forEach { item ->
            val selected = selectedId == item.id
            val editing = typing && selected
            val latest by rememberUpdatedState(item)
            val selectedNow by rememberUpdatedState(selected)
            var dragPx by remember(item.id) { mutableStateOf(Offset.Zero) }
            var liveScale by remember(item.id) { mutableStateOf<Float?>(null) }
            var liveRotation by remember(item.id) { mutableStateOf<Float?>(null) }
            val drawScale = liveScale ?: item.scale
            val drawRotation = liveRotation ?: item.rotation
            // Тот же scale, что в JPEG — без graphicsLayer-scale (иначе другой wrap).
            val drawItem = item.copy(scale = drawScale, rotation = 0f)
            val box = measureTextOverlayBox(drawItem, frameWi, frameHi)
            val chromePadPx = with(density) { 4.dp.toPx() }
            val hitW = (box.width + chromePadPx * 2f).coerceAtLeast(with(density) { 12.dp.toPx() })
            val hitH = (box.height + chromePadPx * 2f).coerceAtLeast(with(density) { 18.dp.toPx() })
            val halfW = hitW / 2f
            val halfH = hitH / 2f
            val cx = frameLeft + item.x * frameW + dragPx.x
            val cy = frameTop + item.y * frameH + dragPx.y
            val center = Offset(cx, cy)
            val corners = listOf(
                Offset(-1f, -1f),
                Offset(1f, -1f),
                Offset(1f, 1f),
                Offset(-1f, 1f),
            ).map { corner ->
                textRotateOffset(Offset(corner.x * halfW, corner.y * halfH), drawRotation) + center
            }
            Box(
                Modifier
                    .zIndex(if (selected) 4f else 2f)
                    .offset {
                        IntOffset((cx - halfW).roundToInt(), (cy - halfH).roundToInt())
                    }
                    .size(with(density) { hitW.toDp() }, with(density) { hitH.toDp() })
                    .graphicsLayer {
                        rotationZ = drawRotation
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin.Center
                    }
                    .then(
                        if (!interactive || editing) Modifier
                        else Modifier.pointerInput(item.id, frameW, frameH) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                val slop = viewConfiguration.touchSlop
                                var dragging = false
                                var acc = Offset.Zero
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.changes.count { it.pressed } >= 2) {
                                        if (dragging) {
                                            val cur = latest
                                            onUpdateState(
                                                cur.id,
                                                cur.x + acc.x / frameW,
                                                cur.y + acc.y / frameH,
                                                null,
                                                null,
                                                true,
                                            )
                                            dragPx = Offset.Zero
                                        }
                                        return@awaitEachGesture
                                    }
                                    val change = event.changes.firstOrNull { it.id == down.id }
                                        ?: return@awaitEachGesture
                                    if (!change.pressed) {
                                        if (dragging) {
                                            val cur = latest
                                            onUpdateState(
                                                cur.id,
                                                cur.x + acc.x / frameW,
                                                cur.y + acc.y / frameH,
                                                null,
                                                null,
                                                true,
                                            )
                                            dragPx = Offset.Zero
                                        } else if (selectedNow) {
                                            onTypeText(item.id)
                                        } else {
                                            onSelect(item.id)
                                        }
                                        return@awaitEachGesture
                                    }
                                    val delta = change.positionChange()
                                    change.consume()
                                    val fromDown = change.position - down.position
                                    if (!dragging && hypot(fromDown.x, fromDown.y) > slop) {
                                        dragging = true
                                        if (!selectedNow) onSelect(item.id)
                                    }
                                    if (dragging && (delta.x != 0f || delta.y != 0f)) {
                                        val cur = latest
                                        val parent = textRotateOffset(delta, cur.rotation)
                                        acc += parent
                                        dragPx = acc
                                    }
                                }
                            }
                        },
                    ),
            ) {
                if (editing) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        TemplateArtwork(
                            item.templateId,
                            item.text,
                            templateInk(item.templateId, item.color),
                            item.fontId,
                            item.styleId,
                            item.outlined,
                            with(density) {
                                textOverlaySizePx(sizeRefPx, drawScale).toSp()
                            },
                            editing = true,
                            onTextChange = onTextChange,
                            editFocus = editFocus,
                        )
                    }
                } else {
                    Canvas(Modifier.fillMaxSize()) {
                        if (interactive && selected) {
                            drawRoundRect(
                                Color.White,
                                style = Stroke(width = chromeStroke),
                                cornerRadius = CornerRadius(
                                    4.dp.toPx() / z,
                                    4.dp.toPx() / z,
                                ),
                            )
                        }
                        drawIntoCanvas { conf ->
                            val c = conf.nativeCanvas
                            c.save()
                            c.translate(size.width / 2f, size.height / 2f)
                            drawTextOverlayContent(c, drawItem, frameWi, frameHi)
                            c.restore()
                        }
                    }
                }
            }
            if (interactive && selected) {
                val centerState by rememberUpdatedState(center)
                val layerCoordsState by rememberUpdatedState(layerCoords)
                // Углы — только scale; отдельная ручка снизу — только rotate.
                corners.forEachIndexed { index, at ->
                    var handleCoords by remember(item.id, index) {
                        mutableStateOf<LayoutCoordinates?>(null)
                    }
                    Box(
                        Modifier
                            .zIndex(5f)
                            .offset {
                                IntOffset(
                                    (at.x - handleR).roundToInt(),
                                    (at.y - handleR).roundToInt(),
                                )
                            }
                            .size(with(density) { handleSizePx.toDp() })
                            .onGloballyPositioned { handleCoords = it }
                            .clip(CircleShape)
                            .background(Color.White)
                            .border(
                                with(density) { (1.dp.toPx() / z).toDp() },
                                Color(0xFFBDBDBD),
                                CircleShape,
                            )
                            .pointerInput(item.id, index) {
                                detectOverlayScaleGesture(
                                    center = { centerState },
                                    layerCoords = { layerCoordsState },
                                    handleCoords = { handleCoords },
                                    startScale = { latest.scale },
                                    onScale = { scale ->
                                        liveScale = scale
                                        onUpdateState(
                                            latest.id, null, null, scale, null, false,
                                        )
                                    },
                                    onEnd = {
                                        onUpdateState(
                                            latest.id,
                                            null,
                                            null,
                                            liveScale ?: latest.scale,
                                            null,
                                            true,
                                        )
                                        liveScale = null
                                    },
                                )
                            },
                    )
                }
                val rotateAt = center + textRotateOffset(
                    Offset(0f, halfH + handleSizePx * 1.15f),
                    drawRotation,
                )
                var rotateCoords by remember(item.id) {
                    mutableStateOf<LayoutCoordinates?>(null)
                }
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .zIndex(5f)
                        .offset {
                            IntOffset(
                                (rotateAt.x - handleR).roundToInt(),
                                (rotateAt.y - handleR).roundToInt(),
                            )
                        }
                        .size(with(density) { handleSizePx.toDp() })
                        .onGloballyPositioned { rotateCoords = it }
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(
                            with(density) { (1.dp.toPx() / z).toDp() },
                            Color(0xFFBDBDBD),
                            CircleShape,
                        )
                        .pointerInput(item.id) {
                            detectOverlayRotateGesture(
                                center = { centerState },
                                layerCoords = { layerCoordsState },
                                handleCoords = { rotateCoords },
                                startRotation = { latest.rotation },
                                onRotation = { rot ->
                                    liveRotation = rot
                                    onUpdateState(
                                        latest.id, null, null, null, rot, false,
                                    )
                                },
                                onEnd = {
                                    onUpdateState(
                                        latest.id,
                                        null,
                                        null,
                                        null,
                                        liveRotation ?: latest.rotation,
                                        true,
                                    )
                                    liveRotation = null
                                },
                            )
                        },
                ) {
                    Icon(
                        painterResource(R.drawable.editor_rotate),
                        contentDescription = stringResource(R.string.editor_rotate),
                        tint = Color.Black,
                        modifier = Modifier.size(with(density) { (handleSizePx * 0.55f).toDp() }),
                    )
                }
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    shadowElevation = 3.dp,
                    modifier = Modifier
                        .zIndex(6f)
                        .graphicsLayer {
                            scaleX = 1f / z
                            scaleY = 1f / z
                            transformOrigin =
                                androidx.compose.ui.graphics.TransformOrigin.Center
                        }
                        .offset {
                            IntOffset(
                                (cx - with(density) { 48.dp.toPx() }).roundToInt(),
                                (cy - halfH - with(density) { 40.dp.toPx() / z })
                                    .coerceAtLeast(with(density) { 4.dp.toPx() })
                                    .roundToInt(),
                            )
                        },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { onDuplicate(item.id) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.editor_sticker_copy),
                                contentDescription = stringResource(R.string.editor_sticker_copy),
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        IconButton(
                            onClick = { onDelete(item.id) },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                painterResource(R.drawable.delete),
                                contentDescription = stringResource(R.string.editor_sticker_delete),
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun textRotateOffset(point: Offset, degrees: Float): Offset {
    val rad = Math.toRadians(degrees.toDouble())
    val cos = kotlin.math.cos(rad).toFloat()
    val sin = kotlin.math.sin(rad).toFloat()
    return Offset(point.x * cos - point.y * sin, point.x * sin + point.y * cos)
}

private fun textNormalizeAngleRad(delta: Float): Float {
    var d = delta
    val pi = Math.PI.toFloat()
    while (d > pi) d -= 2f * pi
    while (d < -pi) d += 2f * pi
    return d
}

fun paintTextOverlay(canvas: AndroidCanvas, item: TextOverlay, width: Int, height: Int) {
    canvas.save()
    canvas.translate(item.x * width, item.y * height)
    canvas.rotate(item.rotation)
    drawTextOverlayContent(canvas, item, width, height)
    canvas.restore()
}

/** Размер рамки текста (как в JPEG) в координатах кадра width×height. */
fun measureTextOverlayBox(item: TextOverlay, width: Int, height: Int): Size {
    val built = buildTextOverlayLayout(item, width)
    return Size(built.boxW, built.boxH)
}

/**
 * Рисует текст+шаблон с центром в (0,0) — тот же путь, что JPEG.
 * Поворот item.rotation снаружи (Compose graphicsLayer / translate+rotate в paintTextOverlay).
 */
fun drawTextOverlayContent(canvas: AndroidCanvas, item: TextOverlay, width: Int, height: Int) {
    val built = buildTextOverlayLayout(item, width)
    val paint = built.paint
    val layout = built.layout
    val dateLayout = built.dateLayout
    val contentW = built.contentW
    val contentH = built.contentH
    val boxW = built.boxW
    val boxH = built.boxH
    val maxWidth = built.maxWidth
    val layoutW = layout.width.toFloat().coerceAtLeast(1f)
    val dateLayoutW = dateLayout?.width?.toFloat()?.coerceAtLeast(1f) ?: layoutW

    canvas.save()
    if (item.templateId == "boom") canvas.rotate(-8f)
    paintTemplateBackground(
        canvas,
        item.templateId,
        -boxW / 2f,
        -boxH / 2f,
        boxW / 2f,
        boxH / 2f,
    )
    canvas.translate(0f, -contentH / 2f)
    var y = 0f
    dateLayout?.let {
        canvas.save()
        canvas.translate(-dateLayoutW / 2f, y)
        it.draw(canvas)
        canvas.restore()
        y += it.height
    }
    canvas.translate(-layoutW / 2f, y)
    if (item.outlined || item.styleId == "outlineBlue" || item.fontId == "giant" || item.fontId == "aemstel" ||
        item.templateId == "paint"
    ) {
        paint.style = AndroidPaint.Style.STROKE
        paint.strokeWidth = paint.textSize * 0.06f
        paint.shader = null
        val strokeLayout =
            StaticLayout.Builder.obtain(item.text, 0, item.text.length, paint, maxWidth)
                .setAlignment(Layout.Alignment.ALIGN_CENTER)
                .build()
        strokeLayout.draw(canvas)
        paint.style = AndroidPaint.Style.FILL
    }
    val colors = exportColors(item.styleId)
    if (colors != null) {
        paint.shader = LinearGradient(
            0f,
            0f,
            contentW,
            layout.height.toFloat(),
            colors,
            null,
            Shader.TileMode.CLAMP
        )
    } else {
        paint.shader = null
        paint.color = templateInk(item.templateId, item.color).toInt()
    }
    layout.draw(canvas)
    canvas.restore()
}

private data class TextOverlayBuilt(
    val paint: TextPaint,
    val layout: StaticLayout,
    val dateLayout: StaticLayout?,
    val maxWidth: Int,
    val contentW: Float,
    val contentH: Float,
    val boxW: Float,
    val boxH: Float,
)

private fun buildTextOverlayLayout(item: TextOverlay, width: Int): TextOverlayBuilt {
    val paint = TextPaint(AndroidPaint.ANTI_ALIAS_FLAG).apply {
        typeface = typefaceOf(item.fontId)
        textAlign = AndroidPaint.Align.LEFT
        textSize = textOverlaySizePx(width.toFloat(), item.scale)
        color = templateInk(item.templateId, item.color).toInt()
        letterSpacing = fontOf(item.fontId).spacing
    }
    // До ~95% кадра — одна строка при увеличении; перенос только когда уже не влезает.
    val wrapLimit = (width * 0.95f).toInt().coerceAtLeast(1)
    val desired = if (item.text.isEmpty()) {
        1
    } else {
        item.text.split('\n').maxOf { line ->
            kotlin.math.ceil(
                StaticLayout.getDesiredWidth(line.ifEmpty { " " }, paint).toDouble(),
            ).toInt()
        }.coerceAtLeast(1)
    }
    val maxWidth = minOf(desired + 2, wrapLimit).coerceAtLeast(1)
    val layout = StaticLayout.Builder.obtain(item.text, 0, item.text.length, paint, maxWidth)
        .setAlignment(Layout.Alignment.ALIGN_CENTER)
        .build()

    val datePaint = TextPaint(paint).apply {
        textSize = paint.textSize * 0.78f
        color = 0xFFFFFFFF.toInt()
        typeface = Typeface.DEFAULT_BOLD
        shader = null
    }
    val dateText = if (item.templateId == "date") {
        val today = java.time.LocalDate.now()
        "${today.year}/${today.monthValue}/${today.dayOfMonth}"
    } else {
        null
    }
    val dateLayout = dateText?.let {
        val dateDesired = kotlin.math.ceil(StaticLayout.getDesiredWidth(it, datePaint).toDouble())
            .toInt()
            .coerceAtLeast(1)
        val dateMax = minOf(dateDesired + 2, wrapLimit).coerceAtLeast(1)
        StaticLayout.Builder.obtain(it, 0, it.length, datePaint, dateMax)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .build()
    }
    val contentW = maxOf(layoutTightWidth(layout), dateLayout?.let { layoutTightWidth(it) } ?: 0f)
        .coerceAtLeast(1f)
    val contentH = layout.height + (dateLayout?.height ?: 0).toFloat()
    val (padH, padV) = templatePadPx(item.templateId, paint.textSize)
    return TextOverlayBuilt(
        paint = paint,
        layout = layout,
        dateLayout = dateLayout,
        maxWidth = maxWidth,
        contentW = contentW,
        contentH = contentH,
        boxW = contentW + padH * 2f,
        boxH = contentH + padV * 2f,
    )
}

private fun layoutTightWidth(layout: StaticLayout): Float {
    var w = 0f
    for (i in 0 until layout.lineCount) {
        w = maxOf(w, layout.getLineWidth(i))
    }
    return w
}

private fun exportColors(styleId: String): IntArray? = when (styleId) {
    "gold" -> intArrayOf(0xFFFFF59D.toInt(), 0xFFF9A825.toInt())
    "rainbow" -> intArrayOf(
        0xFFE53935.toInt(),
        0xFFFDD835.toInt(),
        0xFF43A047.toInt(),
        0xFF1E88E5.toInt()
    )

    "aqua" -> intArrayOf(0xFF80DEEA.toInt(), 0xFFFFF176.toInt())
    "sunset" -> intArrayOf(0xFFFF8A65.toInt(), 0xFFE53935.toInt())
    "split" -> intArrayOf(0xFFEC407A.toInt(), 0xFFFDD835.toInt())
    "neon", "scriptGlow" -> intArrayOf(0xFFFFFFFF.toInt(), 0xFFE040FB.toInt())
    "glitterPink" -> intArrayOf(0xFFF8BBD0.toInt(), 0xFFEC407A.toInt())
    "glitterPurple" -> intArrayOf(0xFFE1BEE7.toInt(), 0xFF8E24AA.toInt())
    "brush" -> intArrayOf(0xFFFFA726.toInt(), 0xFFE53935.toInt())
    "paint", "blocks" -> intArrayOf(
        0xFF00E5FF.toInt(),
        0xFF76FF03.toInt(),
        0xFFFFEA00.toInt(),
        0xFFFF6D00.toInt()
    )

    else -> null
}
