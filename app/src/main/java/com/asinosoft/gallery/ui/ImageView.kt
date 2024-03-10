package com.asinosoft.gallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.toSize
import coil.compose.AsyncImage
import com.asinosoft.gallery.data.Image
import kotlin.math.max
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ImageView(
    image: Image,
    canBeSwiped: (can: Boolean) -> Unit
) {
    var viewSize by remember { mutableStateOf(Size.Zero) }
    var imageSize by remember { mutableStateOf(Size.Zero) }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = max(1f, scale * zoom)
        canBeSwiped(scale.equals(1f))

        val bounds: Size = imageSize - viewSize / scale
        offset = (offset + pan).within(
            Size(
                max(0f, bounds.width),
                max(0f, bounds.height),
            )
        )
    }

    Box {
        AsyncImage(
            model = image.path,
            contentDescription = "",
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .scale(scale)
                .offset { offset.round() }
                .onGloballyPositioned {
                    viewSize = it.size.toSize()
                    imageSize = Size(image.width.toFloat(), image.height.toFloat()) * min(
                        viewSize.width / image.width,
                        viewSize.height / image.height
                    )
                }
                .transformable(
                    state = state,
                    canPan = { !scale.equals(1f) },
                )
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
