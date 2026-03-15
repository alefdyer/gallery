package com.asinosoft.gallery.data

import android.icu.text.DateFormatSymbols
import java.time.LocalDate

fun List<Media>.groupByMonth(): List<ListItem> {
    val result = ArrayList<ListItem>()
    val monthNames =
        DateFormatSymbols
            .getInstance()
            .getMonths(DateFormatSymbols.STANDALONE, DateFormatSymbols.WIDE)
    var month: LocalDate? = null

    sortedByDescending { it.date }.forEach {
        if (it.date.year != month?.year || it.date.month != month.month) {
            month = LocalDate.of(it.date.year, it.date.month, 1)
            result.add(HeaderItem("${monthNames[month.monthValue - 1]} ${month.year}"))
        }

        result.add(MediaItem(it))
    }

    return result
}
