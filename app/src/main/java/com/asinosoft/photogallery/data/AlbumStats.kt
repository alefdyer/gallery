package com.asinosoft.photogallery.data

import java.time.LocalDate

data class AlbumStats(
    val count: Int,
    val size: Long,
    val coverId: Long,
    val date: LocalDate
)
