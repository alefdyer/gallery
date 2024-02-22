package com.asinosoft.gallery.data

import android.net.Uri
import androidx.compose.ui.unit.IntSize
import java.time.LocalDate

data class Image(
    val url: Uri,
    val time: LocalDate,
    val size: IntSize,
)
