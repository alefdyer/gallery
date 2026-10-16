package com.asinosoft.gallery.ui.component

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.core.content.edit
import com.asinosoft.gallery.data.Media
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object GridColumns {
    const val MIN = 1
    const val MAX = 6
    const val DEFAULT = 3

    private const val PREFS = "grid_columns"
    private const val KEY = "columns"

    private val _columns = MutableStateFlow(DEFAULT)
    val columns: StateFlow<Int> = _columns.asStateFlow()

    @Volatile
    private var ready = false

    fun ensureLoaded(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            _columns.value = prefs(context).getInt(KEY, DEFAULT).coerceIn(MIN, MAX)
            ready = true
        }
    }

    fun set(context: Context, value: Int) {
        val columns = value.coerceIn(MIN, MAX)
        if (columns == _columns.value) return
        _columns.value = columns
        prefs(context).edit { putInt(KEY, columns) }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/** Текущее число столбцов сетки (общее для всех списков фото). */
@Composable
fun rememberGridColumns(): Int {
    val context = LocalContext.current
    remember { GridColumns.ensureLoaded(context) }
    return GridColumns.columns.collectAsState().value
}

/**
 * Щипок меняет число столбцов 1…6.
 * Разведение пальцев — крупнее (меньше столбцов), сведение — мельче.
 * Короткий fade и вибрация при каждом шаге; позиция списка сохраняется по id файла.
 */
@Composable
fun Modifier.pinchGridColumns(
    items: List<Media>,
    gridState: LazyGridState,
    columns: Int = rememberGridColumns(),
): Modifier {
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val alpha = remember { Animatable(1f) }
    val currentItems by rememberUpdatedState(items)
    val currentColumns by rememberUpdatedState(columns)
    var zoomAccum by remember { mutableFloatStateOf(1f) }
    var pendingAnchorId by remember { mutableStateOf<Long?>(null) }
    var skipFirstFade by remember { mutableStateOf(true) }

    LaunchedEffect(columns) {
        val anchorId = pendingAnchorId
        pendingAnchorId = null
        if (skipFirstFade) {
            skipFirstFade = false
        } else {
            alpha.snapTo(0.35f)
            alpha.animateTo(1f, tween(GRID_FADE_MS))
        }
        if (null != anchorId) {
            val index = currentItems.indexOfFirst { it.id == anchorId }
            if (index >= 0) gridState.scrollToItem(index)
        }
    }

    return this
        .graphicsLayer { this.alpha = alpha.value }
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false)
                zoomAccum = 1f

                do {
                    val event = awaitPointerEvent()
                    if (event.changes.size < 2) continue

                    zoomAccum *= event.calculateZoom()
                    event.changes.fastForEach {
                        if (it.positionChanged()) it.consume()
                    }

                    val next = when {
                        zoomAccum >= PINCH_STEP && currentColumns > GridColumns.MIN ->
                            currentColumns - 1

                        zoomAccum <= 1f / PINCH_STEP && currentColumns < GridColumns.MAX ->
                            currentColumns + 1

                        else -> null
                    }
                    if (null != next) {
                        pendingAnchorId = currentItems
                            .getOrNull(gridState.firstVisibleItemIndex)
                            ?.id
                        GridColumns.set(context, next)
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        zoomAccum = 1f
                    }
                } while (event.changes.fastAny { it.pressed })

                zoomAccum = 1f
            }
        }
}

private const val PINCH_STEP = 1.18f
private const val GRID_FADE_MS = 140
