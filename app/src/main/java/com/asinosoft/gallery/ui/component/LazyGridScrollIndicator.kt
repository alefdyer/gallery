package com.asinosoft.gallery.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.text.font.FontWeight
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
                delay(1200.milliseconds)
                showThumb = false
                showLabel = false
            }
        }
    }

    val animatedThumbSize by animateDpAsState(
        targetValue = if (isDragged) 44.dp else 36.dp,
        label = "thumbSize"
    )

    AnimatedVisibility(
        visible = showThumb,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxHeight()) {
            val thumbTravelPx = (constraints.maxHeight.toFloat() - with(density) { animatedThumbSize.toPx() }).coerceAtLeast(1f)
            val thumbOffset = (maxHeight - animatedThumbSize) * scrollOffset / contentSize

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
                shadowElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 12.dp, y = thumbOffset)
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
                    painter = painterResource(R.drawable.height),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(animatedThumbSize - 16.dp)
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
                            .padding(top = 2.dp, end = 56.dp)
                            .offset(y = thumbOffset),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                        shape = RoundedCornerShape(50),
                        shadowElevation = 8.dp,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f)
                    ) {
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}
