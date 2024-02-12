package com.asinosoft.gallery.data

import android.net.Uri
import java.time.LocalDate

data class Image(
    val url: Uri,
    val time: LocalDate,
    val width: Int,
    val height: Int,
    val resolution: String?,
    val owner: String?,
    val volume: String?,
)
