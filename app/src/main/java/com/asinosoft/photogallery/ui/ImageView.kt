package com.asinosoft.photogallery.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.AwaitPointerEventScope
import androidx.compose.ui.input.pointer.PointerId
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.fastAll
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.asinosoft.photogallery.data.EXTERNAL_STORAGE_ID
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.model.MediaViewModel
import com.asinosoft.photogallery.ui.component.LocalDismissDrag
import com.asinosoft.photogallery.ui.component.dismissDragTransform
import com.asinosoft.photogallery.ui.component.mediaSharedElement
import com.asinosoft.photogallery.ui.component.rememberCachedThumbnail
import com.asinosoft.photogallery.ui.component.thumbnailCacheKey
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun ImageView(
    media: Media,
    modifier: Modifier = Modifier,
    model: MediaViewModel = hiltViewModel(),
    onTap: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val thumbnail = rememberCachedThumbnail(media)
    var viewSize by remember { mutableStateOf(Size.Zero) }
    var imageSize by remember(media.id) { mutableStateOf(thumbnail?.intrinsicSize ?: Size.Zero) }
    var loaded by remember(media.id) { mutableStateOf(false) }

    val aspectRatio = when {
        imageSize.isSpecified && !imageSize.isEmpty() -> imageSize.width / imageSize.height
        else -> media.image?.aspectRatio()
    }
    val boxSize by remember(aspectRatio) {
        derivedStateOf { aspectRatio?.let { viewSize.fit(it) } ?: viewSize }
    }
    val maxScale by remember {
        derivedStateOf {
            if (boxSize.isEmpty()) {
                MIN_MAX_SCALE
            } else {
                maxOf(
                    viewSize.width / boxSize.width,
                    viewSize.height / boxSize.height,
                    if (imageSize.isSpecified) MIN_MAX_SCALE * imageSize.width / boxSize.width else 0f,
                    MIN_MAX_SCALE
                )
            }
        }
    }
    var scale by remember { mutableFloatStateOf(1f) }

    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var isLoading by remember { mutableStateOf(false) }

    var request by remember(media.id) { mutableStateOf<ImageRequest?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(media.id, media.uri) {
        val key = if (EXTERNAL_STORAGE_ID == media.storageId) "external#${media.uri}" else "media#${media.id}"
        try {
            isLoading = true
            val uri = model.getMediaUri(media)

            request = ImageRequest.Builder(context)
                .data(uri)
                .size(2000)
                .memoryCacheKey(key)
                .diskCacheKey(key)
                .placeholderMemoryCacheKey(thumbnailCacheKey(media))
                .allowHardware(true)
                .listener(
                    onError = { _, result ->
                        error = result.throwable.message
                        isLoading = false
                    },
                    onSuccess = { _, _ ->
                        isLoading = false
                        loaded = true
                    }
                )
                .build()
        } catch (ex: Throwable) {
            error = ex.message
            isLoading = false
        }
    }

    var showProgress by remember { mutableStateOf(false) }
    LaunchedEffect(isLoading) {
        if (isLoading) delay(PROGRESS_DELAY_MS.milliseconds)
        showProgress = isLoading
    }

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
                .onSizeChanged { viewSize = it.toSize() }
                .detectTapAndDoubleTap(
                    onTap = onTap,
                    onDoubleTap = toggleScale
                )
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
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .then(aspectRatio?.let { Modifier.aspectRatio(it) } ?: Modifier.fillMaxSize())
                .dismissDragTransform(LocalDismissDrag.current)
                .mediaSharedElement(media.id)
        ) {
            if (!loaded && null != thumbnail) {
                Image(
                    painter = thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            AsyncImage(
                model = request,
                contentDescription = "",
                contentScale = ContentScale.Crop,
                onState = { state -> state.painter?.let { imageSize = it.intrinsicSize } },
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offsetX.value
                            translationY = offsetY.value
                        }
            )
        }

        error?.let { error ->
            Text(
                text = error,
                color = Color.Red,
                modifier = Modifier.align(Alignment.Center)
            )
        }

        if (showProgress) {
            CircularProgressIndicator(
                color = Color.White,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

/** Пропорции с учётом поворота по EXIF */
private fun com.asinosoft.photogallery.data.Image.aspectRatio(): Float? {
    if (width <= 0 || height <= 0) return null
    return if (90 == orientation || 270 == orientation) {
        height.toFloat() / width
    } else {
        width.toFloat() / height
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
private const val PROGRESS_DELAY_MS = 400

/**
 * Enhanced double tap detector: doesn't cancel gesture when the pointer position changes
 */
private fun Modifier.detectTapAndDoubleTap(
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
