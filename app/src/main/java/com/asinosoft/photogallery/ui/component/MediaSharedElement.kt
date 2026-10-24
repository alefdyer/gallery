@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.asinosoft.photogallery.ui.component

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalContext
import coil3.SingletonImageLoader
import coil3.compose.asPainter
import coil3.memory.MemoryCache
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.ThumbnailCache
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

/** Scope перехода экрана, на котором находится общий элемент */
val LocalNavAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** Видимость общего элемента, если ей управляет сам экран, а не переход навигации */
val LocalSharedElementVisible = compositionLocalOf<Boolean?> { null }

/** Общие элементы включены только при переходе между сеткой и просмотром */
val LocalSharedElementsEnabled = compositionLocalOf { false }

val LocalDismissDrag = compositionLocalOf<DismissDragState?> { null }

private val MediaBoundsTransform =
    BoundsTransform { _, _ -> tween(SHARED_ELEMENT_MS, easing = FastOutSlowInEasing) }

/** Ключ миниатюры в кэше памяти Coil */
fun thumbnailCacheKey(media: Media) = "media-${media.id}"

/**
 * Миниатюра, доступная уже в первом кадре: показывается, пока грузится полное фото.
 * Запрос через Coil здесь не подходит: миниатюру меньше запрошенного размера он считает промахом кэша.
 * Если кэш памяти её уже вытеснил, читаем сохранённый файл миниатюры.
 */
@Composable
fun rememberCachedThumbnail(media: Media): Painter? {
    val context = LocalContext.current
    return remember(media.id) {
        SingletonImageLoader.get(context).memoryCache
            ?.get(MemoryCache.Key(thumbnailCacheKey(media)))
            ?.image
            ?.asPainter(context)
            ?: ThumbnailCache.getFile(context, media.id)
                .takeIf { it.exists() }
                ?.let { runCatching { BitmapFactory.decodeFile(it.path) }.getOrNull() }
                ?.let { BitmapPainter(it.asImageBitmap()) }
    }
}

/**
 * Экран ещё появляется. Тяжёлую работу откладываем до конца перехода, чтобы не дёргать анимацию.
 * Сравниваем состояния, а не isRunning: в первом кадре переход ещё не запущен.
 */
@Composable
fun isOpeningTransition(): Boolean {
    val transition = LocalNavAnimatedScope.current?.transition ?: return false
    return EnterExitState.Visible == transition.targetState && EnterExitState.Visible != transition.currentState
}

/** Общие элементы внутри одного экрана, где просмотр лежит поверх сетки */
@Composable
fun MediaSharedTransitionLayout(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(
            LocalSharedTransitionScope provides this,
            LocalSharedElementsEnabled provides true,
            content = content
        )
    }
}

/**
 * Фото, которое открывают или к которому возвращаются из просмотра.
 * Общий элемент вешается только на него: на каждой клетке сетки он тормозил прокрутку.
 */
object ActiveSharedMedia {
    var id by mutableStateOf<Long?>(null)
}

@Composable
fun Modifier.mediaSharedElement(mediaId: Long): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    if (!LocalSharedElementsEnabled.current) return this
    val visible = LocalSharedElementVisible.current
    val animatedScope = LocalNavAnimatedScope.current
    if (null == visible && null == animatedScope) return this
    val active by remember(mediaId) { derivedStateOf { mediaId == ActiveSharedMedia.id } }
    if (null == visible && !active) return this

    return with(shared) {
        val state = rememberSharedContentState("media-$mediaId")
        if (null != visible) {
            this@mediaSharedElement.sharedElementWithCallerManagedVisibility(
                sharedContentState = state,
                visible = visible,
                boundsTransform = MediaBoundsTransform
            )
        } else {
            this@mediaSharedElement.sharedElement(
                sharedContentState = state,
                animatedVisibilityScope = animatedScope!!,
                boundsTransform = MediaBoundsTransform
            )
        }
    }
}

/** Смещение и уменьшение фото при протягивании вниз */
@Stable
class DismissDragState {
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    var height by mutableFloatStateOf(1f)

    val progress: Float
        get() = (offset.value.y / (height * DISMISS_FULL_PROGRESS)).coerceIn(0f, 1f)

    val scale: Float
        get() = 1f - (1f - DISMISS_MIN_SCALE) * progress

    val isDragging: Boolean
        get() = Offset.Zero != offset.value
}

/**
 * Сдвиг сделан через layout, а не graphicsLayer: так общий элемент
 * начинает анимацию закрытия с того места и размера, где его отпустили
 */
fun Modifier.dismissDragTransform(state: DismissDragState?): Modifier =
    if (null == state) {
        this
    } else {
        layout { measurable, constraints ->
            if (!constraints.hasBoundedWidth || !constraints.hasBoundedHeight) {
                val placeable = measurable.measure(constraints)
                return@layout layout(placeable.width, placeable.height) { placeable.place(0, 0) }
            }
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val scale = state.scale
            val childWidth = (width * scale).roundToInt()
            val childHeight = (height * scale).roundToInt()
            val placeable = measurable.measure(Constraints.fixed(childWidth, childHeight))
            val offset = state.offset.value
            layout(width, height) {
                placeable.place(
                    IntOffset(
                        (width - childWidth) / 2 + offset.x.roundToInt(),
                        (height - childHeight) / 2 + offset.y.roundToInt()
                    )
                )
            }
        }
    }

/** Протягивание вниз закрывает просмотр, вверх — вызывает [onSwipeUp] */
fun Modifier.dragToDismiss(
    state: DismissDragState,
    onDismiss: () -> Unit,
    onSwipeUp: () -> Unit = {},
    ignoreBottom: Int = 0,
): Modifier = pointerInput(state, ignoreBottom) {
    val flingVelocity = DISMISS_FLING_VELOCITY.toPx()
    coroutineScope {
        var fromSystemZone = false
        var dragging = false
        var target = Offset.Zero
        val tracker = VelocityTracker()

        val release = {
            if (dragging) {
                dragging = false
                val velocity = tracker.calculateVelocity().y
                if (target.y > size.height * DISMISS_THRESHOLD || velocity > flingVelocity) {
                    onDismiss()
                } else {
                    launch {
                        state.offset.animateTo(
                            Offset.Zero,
                            spring(stiffness = Spring.StiffnessMediumLow)
                        )
                    }
                }
            }
        }

        detectVerticalDragGestures(
            onDragStart = { position ->
                state.height = size.height.toFloat()
                fromSystemZone = position.y >= size.height - ignoreBottom
                dragging = false
                tracker.resetTracking()
            },
            onDragEnd = { release() },
            onDragCancel = { release() }
        ) { change, amount ->
            if (fromSystemZone) return@detectVerticalDragGestures
            if (!dragging) {
                if (amount < -SWIPE_UP_AMOUNT) onSwipeUp()
                if (amount <= 0f) return@detectVerticalDragGestures
                dragging = true
                target = state.offset.value
            }
            change.consume()
            tracker.addPosition(change.uptimeMillis, change.position)
            target += change.position - change.previousPosition
            val next = target
            launch { state.offset.snapTo(next) }
        }
    }
}

const val SHARED_ELEMENT_MS = 300
private const val DISMISS_FULL_PROGRESS = 0.5f
private const val DISMISS_MIN_SCALE = 0.6f
private const val DISMISS_THRESHOLD = 0.15f
private const val SWIPE_UP_AMOUNT = 10f
private val DISMISS_FLING_VELOCITY = 1000.dp
