package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import com.asinosoft.gallery.data.Media
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Mode { Add, Remove }

private const val SCROLL_EDGE_THRESHOLD_PX = 120f
private const val SCROLL_MAX_STEP_PX = 40f
private const val SCROLL_INTERVAL_MS = 16L

class DragSelectionState {
    var active by mutableStateOf(false)
    private var startIndex by mutableIntStateOf(-1)
    private var startSelection by mutableStateOf<Set<Long>>(setOf())
    private var mode by mutableStateOf<Mode?>(null)

    fun onDragStart(items: List<Media>, selected: Set<Long>, index: Int, mediaId: Long): Set<Long> {
        active = true
        startIndex = index
        mode = if (selected.contains(mediaId)) Mode.Remove else Mode.Add
        startSelection = selected
        return applyDragSelection(
            items,
            startSelection,
            startIndex,
            startIndex,
            mode
        )
    }

    fun onDrag(items: List<Media>, index: Int): Set<Long> =
        applyDragSelection(items, startSelection, startIndex, index, mode)

    fun onDragEnd() {
        active = false
        mode = null
        startSelection = setOf()
    }
}

fun Modifier.dragSelection(
    items: List<Media>,
    state: LazyGridState,
    currentSelection: () -> Set<Long>,
    dragSelectionState: DragSelectionState,
    onSelectedChange: (Set<Long>) -> Unit
): Modifier = pointerInput(items) {
    var dragOffset: Offset? = null
    var autoScrollJob: Job? = null

    detectDragGesturesAfterLongPress(
        onDragStart = { offset ->
            val (index, media) = getMediaAtPosition(offset, state, items)
                ?: return@detectDragGesturesAfterLongPress
            dragOffset = offset
            onSelectedChange(
                dragSelectionState.onDragStart(items, currentSelection(), index, media.id)
            )

            autoScrollJob = CoroutineScope(Dispatchers.Main).launch {
                while (true) {
                    val offset = dragOffset ?: break
                    val scrollDelta = calculateAutoScrollDelta(offset.y, state)
                    if (scrollDelta != 0f) {
                        val scrolled = state.scrollBy(scrollDelta)
                        if (scrolled != 0f) {
                            getMediaAtPosition(
                                offset,
                                state,
                                items
                            )?.first?.let { draggedIndex ->
                                onSelectedChange(dragSelectionState.onDrag(items, draggedIndex))
                            }
                        }
                    }
                    delay(SCROLL_INTERVAL_MS)
                }
            }
        },
        onDrag = { change, _ ->
            change.consume()
            dragOffset = change.position
            val (index, _) = getMediaAtPosition(change.position, state, items)
                ?: return@detectDragGesturesAfterLongPress
            onSelectedChange(dragSelectionState.onDrag(items, index))
        },
        onDragEnd = {
            autoScrollJob?.cancel()
            autoScrollJob = null
            dragOffset = null
            dragSelectionState.onDragEnd()
        },
        onDragCancel = {
            autoScrollJob?.cancel()
            autoScrollJob = null
            dragOffset = null
            dragSelectionState.onDragEnd()
        }
    )
}

private fun calculateAutoScrollDelta(offset: Float, state: LazyGridState): Float {
    val viewportStart = state.layoutInfo.viewportStartOffset.toFloat()
    val viewportEnd = state.layoutInfo.viewportEndOffset.toFloat()
    val topDistance = offset - viewportStart
    val bottomDistance = viewportEnd - offset

    return when {
        topDistance <= SCROLL_EDGE_THRESHOLD_PX -> {
            val normalizedDistance = topDistance.coerceIn(0f, SCROLL_EDGE_THRESHOLD_PX)
            -SCROLL_MAX_STEP_PX * (1f - normalizedDistance / SCROLL_EDGE_THRESHOLD_PX)
        }

        bottomDistance <= SCROLL_EDGE_THRESHOLD_PX -> {
            val normalizedDistance = bottomDistance.coerceIn(0f, SCROLL_EDGE_THRESHOLD_PX)
            SCROLL_MAX_STEP_PX * (1f - normalizedDistance / SCROLL_EDGE_THRESHOLD_PX)
        }

        else -> 0f
    }
}

private fun getMediaAtPosition(
    offset: Offset,
    state: LazyGridState,
    items: List<Media>
): Pair<Int, Media>? {
    val index = state.layoutInfo.visibleItemsInfo.firstOrNull { info ->
        offset.x >= info.offset.x &&
            offset.x <= info.offset.x + info.size.width &&
            offset.y >= info.offset.y &&
            offset.y <= info.offset.y + info.size.height
    }?.index ?: return null

    return Pair(index, items[index])
}

private fun applyDragSelection(
    items: List<Media>,
    selected: Set<Long>,
    start: Int,
    end: Int,
    mode: Mode?
): Set<Long> = when (mode) {
    Mode.Add -> selected + items.mediaIds(start, end)
    Mode.Remove -> selected - items.mediaIds(start, end)
    null -> selected
}

private fun List<Media>.mediaIds(start: Int, end: Int): Set<Long> = subList(
    start.coerceAtMost(end),
    end.coerceAtLeast(start) + 1
).map {
    it.id
}.toSet()
