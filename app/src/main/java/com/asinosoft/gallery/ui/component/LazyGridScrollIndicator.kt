package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.MutatePriority
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.data.ListItem
import com.asinosoft.gallery.data.MediaItem
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private fun firstVisibleMediaDate(
    listItems: List<ListItem>,
    lazyGridState: LazyStaggeredGridState
): LocalDate? {
    var i = lazyGridState.firstVisibleItemIndex
    while (i < listItems.size) {
        when (val entry = listItems[i]) {
            is MediaItem -> return entry.media.date
            else -> i++
        }
    }
    return null
}

private val shortDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)

@Composable
fun LazyGridVerticalScrollIndicator(
    lazyGridState: LazyStaggeredGridState,
    listItems: List<ListItem>,
    modifier: Modifier = Modifier,
    barWidth: Dp = 4.dp
) {
    val indicator = lazyGridState.scrollIndicatorState ?: return
    val scrollOffset = indicator.scrollOffset
    val contentSize = indicator.contentSize
    val viewportSize = indicator.viewportSize

    if (
        scrollOffset == Int.MAX_VALUE ||
        contentSize == Int.MAX_VALUE ||
        viewportSize == Int.MAX_VALUE
    ) {
        return
    }
    if (contentSize <= viewportSize) {
        return
    }

    val maxScroll = (contentSize - viewportSize).toFloat()
    val density = LocalDensity.current
    val minThumbHeight = with(density) { 24.dp.toPx() }

    val scope = rememberCoroutineScope()
    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val thumbColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)

    val dateLabel by remember(listItems) {
        derivedStateOf {
            firstVisibleMediaDate(listItems, lazyGridState)?.format(shortDateFormatter)
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
        val height = constraints.maxHeight.toFloat().coerceAtLeast(0f)
        val thumbHeight = (viewportSize.toFloat() / contentSize.toFloat() * height).coerceIn(
            minThumbHeight,
            height
        )
        val thumbTravel = (height - thumbHeight).coerceAtLeast(0f)
        val thumbOffsetPx =
            ((scrollOffset.toFloat() / maxScroll) * thumbTravel).coerceIn(0f, thumbTravel)
        val thumbCenterPx = thumbOffsetPx + thumbHeight / 2f
        val labelHalf = 11.dp

        Box(
            Modifier
                .fillMaxHeight()
                .pointerInput(Unit) {
                    detectVerticalDragGestures { _, dragAmount ->
                        val th = size.height.toFloat()
                        if (th <= 0f) {
                            return@detectVerticalDragGestures
                        }
                        scope.launch {
                            lazyGridState.scroll(MutatePriority.UserInput) {
                                scrollBy(dragAmount * maxScroll / th)
                            }
                        }
                    }
                }
        ) {
            Canvas(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(barWidth)
            ) {
                val radius = CornerRadius(size.width / 2f, size.width / 2f)
                drawRoundRect(
                    color = trackColor,
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height),
                    cornerRadius = radius
                )
                drawRoundRect(
                    color = thumbColor,
                    topLeft = Offset(0f, thumbOffsetPx),
                    size = Size(size.width, thumbHeight),
                    cornerRadius = radius
                )
            }

            dateLabel?.let { label ->
                val badgeOffsetY = with(density) { thumbCenterPx.toDp() } - labelHalf
                Surface(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(end = barWidth + 6.dp)
                        .offset(y = badgeOffsetY),
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
