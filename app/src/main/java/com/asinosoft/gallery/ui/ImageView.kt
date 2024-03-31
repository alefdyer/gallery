package com.asinosoft.gallery.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastForEach
import coil.compose.AsyncImage
import com.asinosoft.gallery.data.Image
import kotlin.math.max
import kotlin.math.min

@Composable
fun ImageView(image: Image) {
    var viewSize by remember { mutableStateOf(Size.Zero) }
    var imageSize by remember { mutableStateOf(Size.Zero) }
    var bounds by remember { mutableStateOf(Size.Zero) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box {
        AsyncImage(
            model = image.path,
            contentDescription = "",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .offset { offset.round() }
                .onSizeChanged {
                    viewSize = it.toSize()
                    imageSize = Size(image.width.toFloat(), image.height.toFloat()) * min(
                        viewSize.width / image.width, viewSize.height / image.height
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures(
                        onDoubleTap = {
                            scale = if (scale.equals(1f)) 2.3f else 1f
                            bounds = (imageSize - viewSize / scale).positive()
                            offset = offset.within(bounds)
                        }
                    )
                }
                .pointerInput(Unit) {
                    awaitEachGesture {
                        while (true) {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()

                            scale = max(1f, scale * zoom)
                            bounds = (imageSize - viewSize / scale).positive()

                            val x0 = offset.x
                            offset = (offset + pan).within(bounds)

                            val zoomed = !zoom.equals(1f)
                            val panned = offset.x != x0

                            if (zoomed or panned) {
                                event.changes.fastForEach {
                                    if (it.positionChanged()) {
                                        it.consume()
                                    }
                                }
                            }

                        }
                    }
                }
        )
    }
}

fun Offset.within(bounds: Size) = Offset(
    x.within(-bounds.width / 2, bounds.width / 2),
    y.within(-bounds.height / 2, bounds.height / 2),
)

fun Float.within(min: Float, max: Float) = max(min, min(max, this))

operator fun Size.minus(another: Size) = Size(
    width - another.width,
    height - another.height
)

fun Size.positive() = Size(max(0f, width), max(0f, height))
