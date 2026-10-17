package com.asinosoft.gallery.ui.component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.max

/**
 * Двойной тап и щипок масштабируют [content].
 * [frameModifier] — рамка кадра (shared element, dismiss): она не масштабируется.
 *
 * Если заданы [onDoubleTapSeekBackward]/[onDoubleTapSeekForward], экран делится на три
 * вертикальные полосы: левая/правая — перемотка, средняя (на всю высоту) — зум.
 */
@Composable
fun ZoomableMediaBox(
    aspectRatio: Float?,
    modifier: Modifier = Modifier,
    frameModifier: Modifier = Modifier,
    contentScale: Float? = null,
    onTap: () -> Unit = {},
    onDoubleTapSeekBackward: (() -> Unit)? = null,
    onDoubleTapSeekForward: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit
) {
    val scope = rememberCoroutineScope()
    var viewSize by remember { mutableStateOf(Size.Zero) }
    val boxSize by remember(aspectRatio) {
        derivedStateOf { aspectRatio?.let { viewSize.fit(it) } ?: viewSize }
    }
    val maxScale by remember(contentScale) {
        derivedStateOf {
            if (boxSize.isEmpty()) {
                MIN_MAX_SCALE
            } else {
                maxOf(
                    viewSize.width / boxSize.width,
                    viewSize.height / boxSize.height,
                    contentScale ?: 0f,
                    MIN_MAX_SCALE
                )
            }
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    val toggleScale: (Offset) -> Unit = { tapOffset ->
        val oldScale = scale
        scale = if (oldScale > 1f) 1f else maxScale

        val bounds = (boxSize * scale - viewSize).positive()
        offsetX.updateBounds(-bounds.width / 2f, bounds.width / 2)
        offsetY.updateBounds(-bounds.height / 2f, bounds.height / 2)

        if (1f == scale) {
            scope.launch {
                offsetX.snapTo(0f)
                offsetY.snapTo(0f)
            }
        } else {
            val viewCenter = Offset(viewSize.width / 2f, viewSize.height / 2f)
            val newOffsetX = (offsetX.value + viewCenter.x - tapOffset.x) * (scale / oldScale)
            val newOffsetY = (offsetY.value + viewCenter.y - tapOffset.y) * (scale / oldScale)
            scope.launch {
                offsetX.snapTo(newOffsetX)
                offsetY.snapTo(newOffsetY)
            }
        }
    }

    val seekBack = onDoubleTapSeekBackward
    val seekForward = onDoubleTapSeekForward
    val onDoubleTap: (Offset) -> Unit = { tapOffset ->
        if (null != seekBack && null != seekForward && viewSize.width > 0f) {
            val left = viewSize.width / 3f
            val right = viewSize.width * 2f / 3f
            when {
                tapOffset.x < left -> seekBack()
                tapOffset.x > right -> seekForward()
                else -> toggleScale(tapOffset)
            }
        } else {
            toggleScale(tapOffset)
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .onSizeChanged { viewSize = it.toSize() }
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .then(aspectRatio?.let { Modifier.aspectRatio(it) } ?: Modifier.fillMaxSize())
                .then(frameModifier)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX.value
                        translationY = offsetY.value
                    },
                content = content
            )
        }

        // Поверх AndroidView (видео), иначе PlayerView съедает касания
        Box(
            modifier = Modifier
                .fillMaxSize()
                .detectTapAndDoubleTap(onTap = onTap, onDoubleTap = onDoubleTap)
                .pointerInput(boxSize, viewSize) {
                    if (boxSize.isEmpty()) return@pointerInput

                    awaitEachGesture {
                        val trackers = mutableMapOf<PointerId, VelocityTracker>()

                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()

                            scale = max(1f, scale * zoom)
                            val bounds = (boxSize * scale - viewSize).positive()
                            offsetX.updateBounds(-bounds.width / 2f, bounds.width / 2)
                            offsetY.updateBounds(-bounds.height / 2f, bounds.height / 2)

                            scope.launch {
                                offsetX.snapTo(offsetX.value * zoom + pan.x)
                                offsetY.snapTo(offsetY.value * zoom + pan.y)
                            }

                            if (scale != 1f || event.changes.size > 1) {
                                event.changes.fastForEach {
                                    if (it.positionChanged()) it.consume()
                                }
                            }

                            event.changes.fastForEach {
                                val tracker = trackers.getOrPut(it.id) { VelocityTracker() }
                                tracker.addPosition(it.uptimeMillis, it.position)
                            }
                        } while (event.changes.fastAny { it.pressed })

                        val pointerId = currentEvent.changes.first().id
                        trackers[pointerId]?.calculateVelocity()?.let { velocity ->
                            scope.launch {
                                offsetX.animateDecay(velocity.x, exponentialDecay())
                            }
                            scope.launch {
                                offsetY.animateDecay(velocity.y, exponentialDecay())
                            }
                        }
                    }
                }
        )
    }
}

private fun Size.fit(aspectRatio: Float): Size =
    if (width / height > aspectRatio) Size(height * aspectRatio, height) else Size(
        width,
        width / aspectRatio
    )

private operator fun Size.minus(another: Size) = Size(
    width - another.width,
    height - another.height
)

private fun Size.positive() = Size(max(0f, width), max(0f, height))

private const val MIN_MAX_SCALE = 2f

/** Двойной тап не срывается, если палец чуть сдвинулся */
fun Modifier.detectTapAndDoubleTap(
    onTap: () -> Unit,
    onDoubleTap: (Offset) -> Unit
): Modifier = this then Modifier.pointerInput(Unit) {
    awaitEachGesture {
        val down = awaitFirstDown()
        withTimeoutOrNull(300) { waitForUp() }?.let { up ->
            val second = withTimeoutOrNull(300) { awaitFirstDown() }
            if (second != null) {
                onDoubleTap(down.position)
            } else {
                val distance = down.position - up.position
                if (abs(distance.x) + abs(distance.y) < 100) {
                    onTap()
                }
            }
        }
    }
}

private suspend fun AwaitPointerEventScope.waitForUp(): PointerInputChange {
    while (true) {
        val event = awaitPointerEvent()
        if (event.changes.fastAll { it.changedToUp() }) {
            return event.changes[0]
        }
    }
}
