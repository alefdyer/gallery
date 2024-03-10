package com.asinosoft.gallery.util

import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.ImageGroup
import java.time.LocalDate

fun groupByDate(images: List<Image>): List<ImageGroup> {
    val days = HashMap<LocalDate, ArrayList<Image>>()

    images.forEach {
        val day = LocalDate.of(it.date.year, it.date.month, it.date.dayOfMonth)

        days.putIfAbsent(day, arrayListOf())
        days[day]?.add(it)
    }

    return days.map { (day, images) ->
        ImageGroup(
            day,
            images
        )
    }.sortedByDescending { it.date }
}
