package com.asinosoft.gallery.util

import android.icu.text.DateFormatSymbols
import com.asinosoft.gallery.data.HeaderItem
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.ImageItem
import com.asinosoft.gallery.data.ListItem
import java.time.LocalDate

fun groupByMonth(images: List<Image>): List<ListItem> {
    val result = ArrayList<ListItem>()
    val monthNames = DateFormatSymbols.getInstance()
        .getMonths(DateFormatSymbols.STANDALONE, DateFormatSymbols.WIDE)
    var month: LocalDate? = null

    images.sortedByDescending { it.date }.forEach { image ->
        if (image.date.year != month?.year || image.date.month != month.month) {
            month = LocalDate.of(image.date.year, image.date.month, 1)
            result.add(HeaderItem("${monthNames[month.monthValue - 1]} ${month.year}"))
        }

        result.add(ImageItem(image))
    }

    return result
}
