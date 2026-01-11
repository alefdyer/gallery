package com.asinosoft.gallery.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity(
    indices = [
        Index(value = ["path"], unique = true)
    ]
)
data class Image(
    @PrimaryKey
    val id: UUID,
    val path: String,
    val date: LocalDate,
    val time: LocalTime,
    val width: Int,
    val height: Int,
    val orientation: Int,
    val album: String?,
    val size: Long,
    val filename: String,
)