package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Media
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
    modifier: Modifier = Modifier
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
            hideJob?.cancel()
            hideJob = null
        } else {
            hideJob = scope.launch {
                delay(1000.milliseconds)
                showThumb = false
                showLabel = false
            }
        }
    }

    if (showThumb) {
        BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
            val thumbSize = 32.dp
            val thumbSizePx = with(density) { thumbSize.toPx() }
            val thumbTravelPx = (constraints.maxHeight.toFloat() - thumbSizePx).coerceAtLeast(1f)
            val thumbOffset = (maxHeight - thumbSize) * scrollOffset / contentSize

            val draggableState = rememberDraggableState { dragAmount ->
                dragOffsetPx = (dragOffsetPx + dragAmount).coerceIn(0f, thumbTravelPx)
                val fraction = dragOffsetPx / thumbTravelPx
                val targetIndex = (fraction * (listItems.size - 1)).toInt().coerceIn(0, listItems.size - 1)
                
                scope.launch {
                    lazyGridState.scrollToItem(targetIndex)
                }
            }

            Surface(
                shape = CircleShape,
                shadowElevation = 16.dp,
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainer),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 16.dp, y = thumbOffset)
                    .draggable(
                        draggableState,
                        Orientation.Vertical,
                        onDragStarted = {
                            dragOffsetPx = (scrollOffset.toFloat() / contentSize * thumbTravelPx).coerceIn(0f, thumbTravelPx)
                            isDragged = true
                            showLabel = true
                        },
                        onDragStopped = { isDragged = false }
                    )
            ) {
                Icon(
                    painterResource(R.drawable.height),
                    contentDescription = null,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(thumbSize)
                )
            }

            if (showLabel) {
                val dateLabel by remember(listItems, lazyGridState) {
                    derivedStateOf {
                        val index = lazyGridState.firstVisibleItemIndex.coerceIn(0, listItems.size - 1)
                        listItems.getOrNull(index)?.date?.format(shortDateFormatter)
                    }
                }

                dateLabel?.let { label ->
                    Surface(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 8.dp, end = 64.dp)
                            .offset(y = thumbOffset),
                        border = BorderStroke(2.dp, MaterialTheme.colorScheme.surfaceContainer),
                        shape = RoundedCornerShape(50),
                        shadowElevation = 16.dp,
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }
    }
}
