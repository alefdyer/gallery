package com.asinosoft.gallery.util

import android.icu.text.DateFormatSymbols
import com.asinosoft.gallery.data.HeaderItem
import com.asinosoft.gallery.data.ListItem
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.MediaItem
import java.time.LocalDate

fun groupByMonth(media: List<Media>): List<ListItem> {
    val result = ArrayList<ListItem>()
    val monthNames =
        DateFormatSymbols
            .getInstance()
            .getMonths(DateFormatSymbols.STANDALONE, DateFormatSymbols.WIDE)
    var month: LocalDate? = null

    media.sortedByDescending { it.date }.forEach {
        if (it.date.year != month?.year || it.date.month != month.month) {
            month = LocalDate.of(it.date.year, it.date.month, 1)
            result.add(HeaderItem("${monthNames[month.monthValue - 1]} ${month.year}"))
        }

        result.add(MediaItem(it))
    }

    return result
}
