package com.asinosoft.photogallery.data.people

import kotlin.math.roundToInt

data class PeopleIndexProgress(
    val done: Int = 0,
    val total: Int = 0,
) {
    val active: Boolean get() = total > 0 && done < total

    val fraction: Float
        get() = when {
            total <= 0 -> 0f
            done >= total -> 1f
            else -> done.toFloat() / total.toFloat()
        }

    val percent: Int
        get() = (fraction * 100f).roundToInt().coerceIn(0, 100)
}
