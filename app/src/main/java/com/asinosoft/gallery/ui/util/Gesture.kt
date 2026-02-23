package com.asinosoft.gallery.ui.util

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput

internal fun Modifier.onDoubleClick(onDoubleClick: () -> Unit): Modifier =
    this then
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown()
                if (
                    null !== waitForUpOrCancellation() &&
                    null !== withTimeoutOrNull(300) { awaitFirstDown() }
                ) {
                    onDoubleClick()
                }
            }
        }

internal fun Modifier.onSingleClick(onClick: () -> Unit): Modifier =
    this then
        Modifier.pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown()
                if (
                    null !== waitForUpOrCancellation() &&
                    null == withTimeoutOrNull(300) { awaitFirstDown() }
                ) {
                    onClick()
                }
            }
        }
