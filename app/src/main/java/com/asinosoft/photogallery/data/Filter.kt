package com.asinosoft.photogallery.data

import com.asinosoft.photogallery.data.storage.MediaStatistics

data class Filter(
    val application: Application,
    val statistics: MediaStatistics,
    val enabled: Boolean = false,
)
