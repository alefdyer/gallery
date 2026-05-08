package com.asinosoft.gallery.data

import java.time.LocalDate

data class AlbumStats(
    val name: String,
    val count: Int,
    val size: Long,
    val coverId: Long?,
    val date: LocalDate
)
