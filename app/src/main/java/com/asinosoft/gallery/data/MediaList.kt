package com.asinosoft.gallery.data

import android.icu.text.DateFormatSymbols
import java.time.LocalDate

fun List<Media>.groupByMonth(): List<ListItem> {
    val monthNames =
        DateFormatSymbols
            .getInstance()
            .getMonths(DateFormatSymbols.STANDALONE, DateFormatSymbols.WIDE)

    val result = ArrayList<ListItem>()
    groupBy { media ->
        LocalDate.of(
            media.date.year,
            media.date.month,
            1
        )
    }.forEach { (month, media) ->
        result.add(
            HeaderItem(
                "${monthNames[month.monthValue - 1]} ${month.year}",
                media.map { it.id }.toSet()
            )
        )
        result.addAll(media.map { MediaItem(it) })
    }

    return result
}
