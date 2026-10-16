package com.asinosoft.gallery.data

import com.asinosoft.gallery.data.storage.MediaStatistics

data class Filter(
    val application: Application,
    val statistics: MediaStatistics,
    val enabled: Boolean = false,
)
