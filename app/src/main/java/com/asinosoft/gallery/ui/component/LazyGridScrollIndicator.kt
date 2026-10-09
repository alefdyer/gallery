package com.asinosoft.gallery.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.data.Media
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

private val shortDateFormatter: DateTimeFormatter =
    DateTimeFormatter.ofLocalizedDate(FormatStyle.SHORT)

@Composable
fun LazyGridVerticalScrollIndicator(
    lazyGridState: LazyGridState,
    listItems: List<Media>,
    modifier: Modifier = Modifier,
    showDateLabel: Boolean = true,
    onDateClick: (LocalDate) -> Unit = {}
) {
    val indicator = lazyGridState.scrollIndicatorState ?: return
    val scrollMetrics by remember(indicator) {
        derivedStateOf {
            Triple(
                indicator.scrollOffset,
                indicator.contentSize,
                indicator.viewportSize
            )
        }
    }
    val (scrollOffset, contentSize, viewportSize) = scrollMetrics

    if (
        scrollOffset == Int.MAX_VALUE ||
        contentSize == Int.MAX_VALUE ||
        viewportSize == Int.MAX_VALUE ||
        contentSize <= viewportSize ||
        listItems.isEmpty()
    ) {
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    var showThumb by remember { mutableStateOf(false) }
    var showLabel by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    var isDragged by remember { mutableStateOf(false) }
    var dragOffsetPx by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(lazyGridState.isScrollInProgress, isDragged) {
        if (lazyGridState.isScrollInProgress || isDragged) {
            showThumb = true
            showLabel = true
            hideJob?.cancel()
            hideJob = null
        } else {
            hideJob = scope.launch {
                delay(700.milliseconds)
                showLabel = false
                delay(500.milliseconds)
                showThumb = false
            }
        }
    }

    val thumbWidth by animateDpAsState(
        targetValue = if (isDragged) 6.dp else 4.dp,
        label = "thumbWidth"
    )
    val labelEndPadding by animateDpAsState(
        targetValue = if (isDragged) LABEL_END_PADDING_DRAGGED else LABEL_END_PADDING,
        label = "labelEndPadding"
    )
    val thumbColor by animateColorAsState(
        targetValue = if (isDragged) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
        },
        label = "thumbColor"
    )

    AnimatedVisibility(
        visible = showThumb,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxHeight()) {
            val thumbTravelPx = (constraints.maxHeight.toFloat() - with(density) { THUMB_TOUCH_HEIGHT.toPx() }).coerceAtLeast(1f)
            val thumbOffset = (maxHeight - THUMB_TOUCH_HEIGHT) * scrollOffset / contentSize

            val draggableState = rememberDraggableState { dragAmount ->
                dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(0f, thumbTravelPx)
                val fraction = dragOffsetPx / thumbTravelPx
                val targetIndex = (fraction * (listItems.size - 1)).toInt().coerceIn(0, listItems.size - 1)

                scope.launch {
                    lazyGridState.scrollToItem(targetIndex)
                }
            }

            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(y = thumbOffset)
                    .size(width = THUMB_TOUCH_WIDTH, height = THUMB_TOUCH_HEIGHT)
                    .draggable(
                        draggableState,
                        Orientation.Vertical,
                        onDragStarted = {
                            dragOffsetPx = (scrollOffset.toFloat() / contentSize * thumbTravelPx).coerceIn(0f, thumbTravelPx)
                            isDragged = true
                        },
                        onDragStopped = { isDragged = false }
                    )
            ) {
                Box(
                    Modifier
                        .size(width = thumbWidth, height = THUMB_HEIGHT)
                        .background(thumbColor, RoundedCornerShape(50))
                )
            }

            val labelDate by remember(listItems, lazyGridState) {
                derivedStateOf {
                    val index = lazyGridState.firstVisibleItemIndex.coerceIn(0, listItems.size - 1)
                    listItems.getOrNull(index)?.date
                }
            }

            AnimatedVisibility(
                visible = showDateLabel && showLabel && null != labelDate,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = (THUMB_TOUCH_HEIGHT - LABEL_HEIGHT) / 2, end = labelEndPadding)
                    .offset(y = thumbOffset)
            ) {
                Surface(
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)),
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                ) {
                    Text(
                        text = labelDate?.format(shortDateFormatter).orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clickable { labelDate?.let(onDateClick) }
                            .height(LABEL_HEIGHT)
                            .wrapContentHeight()
                            .padding(horizontal = 12.dp)
                    )
                }
            }
        }
    }
}

private val THUMB_HEIGHT = 44.dp
private val THUMB_TOUCH_HEIGHT = 56.dp
private val THUMB_TOUCH_WIDTH = 36.dp
private val LABEL_HEIGHT = 28.dp
private val LABEL_END_PADDING = THUMB_TOUCH_WIDTH
private val LABEL_END_PADDING_DRAGGED = 80.dp
