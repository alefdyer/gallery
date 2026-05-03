package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.MutatePriority
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
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun firstVisibleMediaDate(listItems: List<Media>, lazyGridState: LazyGridState): LocalDate {
    val i = lazyGridState.firstVisibleItemIndex
    return listItems[i].date
}

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
        contentSize <= viewportSize
    ) {
        return
    }

    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val dateLabel by remember(listItems) {
        derivedStateOf {
            firstVisibleMediaDate(listItems, lazyGridState).format(shortDateFormatter)
        }
    }

    var showThumb by remember { mutableStateOf(false) }
    var showLabel by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    var isDragged by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(lazyGridState.isScrollInProgress, isDragged) {
        if (lazyGridState.isScrollInProgress || isDragged) {
            showThumb = true
            hideJob?.cancel()
            hideJob = null
        } else {
            hideJob = scope.launch {
                delay(1000)
                showThumb = false
                showLabel = false
            }
        }
    }

    if (showThumb) {
        BoxWithConstraints(modifier = modifier.fillMaxHeight()) {
            val thumbSize = 32.dp
            val thumbTravel = maxHeight - thumbSize
            val thumbOffset = thumbTravel * scrollOffset / contentSize

            val draggableState = rememberDraggableState { dragAmount ->
                dragOffset += dragAmount
                val offset =
                    dragOffset * contentSize / constraints.maxHeight -
                        (with(density) { thumbSize.toPx() })
                scope.launch {
                    lazyGridState.scroll(MutatePriority.UserInput) {
                        scrollBy(offset - scrollOffset)
                    }
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
