package com.asinosoft.gallery.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.asinosoft.gallery.data.Image
import kotlinx.coroutines.launch
import kotlin.math.max

@Composable
fun ImageView(
    image: Image,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()

    var viewSize by remember { mutableStateOf(Size.Zero) }
    var imageSize by remember { mutableStateOf(Size.Zero) }

    val minScale by remember { derivedStateOf { imageSize.scaleInto(viewSize) } }
    val maxScale by remember { derivedStateOf { imageSize.scaleUpTo(viewSize) } }
    var scale by remember { mutableFloatStateOf(1f) }

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }

    LaunchedEffect(minScale) { scale = minScale }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale.equals(minScale)) maxScale else minScale
                            val bounds = (imageSize * scale - viewSize).positive()
                            offsetX.updateBounds(-bounds.width / 2f, bounds.width / 2)
                            offsetY.updateBounds(-bounds.height / 2f, bounds.height / 2)
                        },
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val trackers = mutableMapOf<PointerId, VelocityTracker>()

                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()

                            scale = max(minScale, scale * zoom)
                            val bounds = (imageSize * scale - viewSize).positive()
                            offsetX.updateBounds(-bounds.width / 2f, bounds.width / 2)
                            offsetY.updateBounds(-bounds.height / 2f, bounds.height / 2)

                            scope.launch {
                                offsetX.snapTo(offsetX.value * zoom + pan.x)
                                offsetY.snapTo(offsetY.value * zoom + pan.y)
                            }

                            if (scale != minScale) {
                                event.changes.fastForEach {
                                    if (it.positionChanged()) {
                                        it.consume()
                                    }
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
    ) {
        AsyncImage(
            model =
                ImageRequest
                    .Builder(LocalContext.current)
                    .data(image.path)
                    .size(2000)
                    .build(),
            clipToBounds = false,
            contentDescription = "",
            contentScale = ContentScale.None,
            onState = { state -> state.painter?.let { imageSize = it.intrinsicSize } },
            modifier =
                Modifier
                    .fillMaxSize()
                    .onSizeChanged { viewSize = it.toSize() }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX.value
                        translationY = offsetY.value
                    }
        )
    }
}

private operator fun Size.minus(another: Size) =
    Size(
        width - another.width,
        height - another.height,
    )

private fun Size.positive() = Size(max(0f, width), max(0f, height))

private fun Size.scaleInto(box: Size): Float = (box.width / width).coerceAtMost(box.height / height)

private fun Size.scaleUpTo(box: Size): Float =
    (box.width / width).coerceAtLeast(box.height / height).coerceAtLeast(2f)
