package com.asinosoft.gallery.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.GalleryViewModel
import kotlin.math.max
import kotlinx.coroutines.launch

@Composable
fun ImageView(
    media: Media,
    modifier: Modifier = Modifier,
    model: GalleryViewModel = hiltViewModel(),
    onTap: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var viewSize by remember { mutableStateOf(Size.Zero) }
    var imageSize by remember { mutableStateOf(Size.Zero) }

    val minScale by remember { derivedStateOf { imageSize.scaleInto(viewSize) } }
    val maxScale by remember { derivedStateOf { imageSize.scaleUpTo(viewSize) } }
    var scale by remember { mutableFloatStateOf(1f) }

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var isLoading by remember { mutableStateOf(false) }

    var request by remember { mutableStateOf<ImageRequest?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(minScale) { scale = minScale }

    LaunchedEffect(media) {
        scope.launch {
            val key = "media#${media.id}"
            try {
                isLoading = true
                val uri = model.getMediaUri(media)

                request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(2000)
                    .memoryCacheKey(key)
                    .diskCacheKey(key)
                    .allowHardware(true)
                    .listener(
                        onError = { _, result ->
                            error = result.throwable.message
                            isLoading = false
                        },
                        onSuccess = { _, _ -> isLoading = false }
                    )
                    .build()
            } catch (ex: Throwable) {
                error = ex.message
                isLoading = false
            }
        }
    }

    val toggleScale: (Offset) -> Unit = { tapOffset ->
        val oldScale = scale
        scale = if (oldScale > minScale) minScale else maxScale

        val bounds = (imageSize * scale - viewSize).positive()
        offsetX.updateBounds(-bounds.width / 2f, bounds.width / 2)
        offsetY.updateBounds(-bounds.height / 2f, bounds.height / 2)

        if (scale == minScale) {
            scope.launch {
                offsetX.snapTo(0f)
                offsetY.snapTo(0f)
            }
        } else {
            val viewCenter = Offset(
                viewSize.width / 2f,
                viewSize.height / 2f
            )
            val newOffsetX =
                (offsetX.value + viewCenter.x - tapOffset.x) *
                    (scale / oldScale)
            val newOffsetY =
                (offsetY.value + viewCenter.y - tapOffset.y) *
                    (scale / oldScale)

            scope.launch {
                offsetX.snapTo(newOffsetX)
                offsetY.snapTo(newOffsetY)
            }
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(imageSize, viewSize) {
                    if (imageSize.isEmpty() or viewSize.isEmpty()) return@pointerInput

                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = toggleScale
                    )
                }
                .pointerInput(imageSize, viewSize) {
                    if (imageSize.isEmpty()) return@pointerInput

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
            model = request,
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

        error?.let { error ->
            Text(
                text = error,
                color = Color.Red,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (isLoading) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

private operator fun Size.minus(another: Size) = Size(
    width - another.width,
    height - another.height
)

private fun Size.positive() = Size(max(0f, width), max(0f, height))

private fun Size.scaleInto(box: Size): Float = (box.width / width).coerceAtMost(box.height / height)

private fun Size.scaleUpTo(box: Size): Float =
    (box.width / width).coerceAtLeast(box.height / height).coerceAtLeast(2f)
