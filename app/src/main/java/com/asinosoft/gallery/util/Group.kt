package com.asinosoft.gallery.util

import android.icu.text.DateFormatSymbols
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.ImageGroup
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

fun groupByDate(images: List<Image>): List<ImageGroup> {
    val groups = HashMap<LocalDate, ArrayList<Image>>()
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

    images.forEach {
        val day = LocalDate.of(it.date.year, it.date.month, it.date.dayOfMonth)

        groups.putIfAbsent(day, arrayListOf())
        groups[day]?.add(it)
    }

    return groups.map { (day, images) ->
        ImageGroup(
            day,
            images,
            day.format(dateFormat)
        )
    }.sortedByDescending { it.date }
}

fun groupByMonth(images: List<Image>): List<ImageGroup> {
    val groups = HashMap<LocalDate, ArrayList<Image>>()
    val monthNames = DateFormatSymbols.getInstance()
        .getMonths(DateFormatSymbols.STANDALONE, DateFormatSymbols.WIDE)

    images.forEach {
        val month = LocalDate.of(it.date.year, it.date.month, 1)

        groups.putIfAbsent(month, arrayListOf())
        groups[month]?.add(it)
    }

    return groups.map { (month, images) ->
        ImageGroup(
            month,
            images,
            "${monthNames[month.monthValue - 1]} ${month.year}"
        )
    }.sortedByDescending { it.date }
}
