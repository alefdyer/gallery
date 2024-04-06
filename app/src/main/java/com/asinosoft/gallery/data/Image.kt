package com.asinosoft.gallery.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity
data class Image(
    @PrimaryKey
    val path: String,
    val date: LocalDate,
    val width: Int,
    val height: Int,
    val orientation: Int,
    val album: String?,
    val size: Long,
)