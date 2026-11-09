package com.asinosoft.photogallery.ui.editor

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.layout.LayoutCoordinates
import kotlin.math.atan2
import kotlin.math.hypot

/** Палец в координатах слоя относительно центра объекта. */
internal fun fingerFromLayerCenter(
    layer: LayoutCoordinates?,
    handle: LayoutCoordinates?,
    localPos: Offset,
    center: Offset,
): Offset? {
    if (layer == null || handle == null) return null
    if (!layer.isAttached || !handle.isAttached) return null
    return layer.localPositionOf(handle, localPos) - center
}

internal suspend fun PointerInputScope.detectOverlayScaleGesture(
    center: () -> Offset,
    layerCoords: () -> LayoutCoordinates?,
    handleCoords: () -> LayoutCoordinates?,
    startScale: () -> Float,
    onScale: (Float) -> Unit,
    onEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val baseScale = startScale()
        val startFinger = fingerFromLayerCenter(
            layerCoords(),
            handleCoords(),
            down.position,
            center(),
        ) ?: return@awaitEachGesture
        val startDist = hypot(startFinger.x, startFinger.y).coerceAtLeast(1f)
        try {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2) break
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUp() || !change.pressed) break
                change.consume()
                val finger = fingerFromLayerCenter(
                    layerCoords(),
                    handleCoords(),
                    change.position,
                    center(),
                ) ?: continue
                val dist = hypot(finger.x, finger.y).coerceAtLeast(1f)
                onScale((baseScale * dist / startDist).coerceIn(0.35f, 4f))
            }
        } finally {
            onEnd()
        }
    }
}

internal suspend fun PointerInputScope.detectOverlayRotateGesture(
    center: () -> Offset,
    layerCoords: () -> LayoutCoordinates?,
    handleCoords: () -> LayoutCoordinates?,
    startRotation: () -> Float,
    onRotation: (Float) -> Unit,
    onEnd: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val baseRot = startRotation()
        val startFinger = fingerFromLayerCenter(
            layerCoords(),
            handleCoords(),
            down.position,
            center(),
        ) ?: return@awaitEachGesture
        val startAngle = atan2(startFinger.y, startFinger.x)
        try {
            while (true) {
                val event = awaitPointerEvent()
                if (event.changes.count { it.pressed } >= 2) break
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (change.changedToUp() || !change.pressed) break
                change.consume()
                val finger = fingerFromLayerCenter(
                    layerCoords(),
                    handleCoords(),
                    change.position,
                    center(),
                ) ?: continue
                val angle = atan2(finger.y, finger.x)
                var delta = angle - startAngle
                val pi = Math.PI.toFloat()
                while (delta > pi) delta -= 2f * pi
                while (delta < -pi) delta += 2f * pi
                val deg = Math.toDegrees(delta.toDouble()).toFloat()
                onRotation(baseRot + deg)
            }
        } finally {
            onEnd()
        }
    }
}
