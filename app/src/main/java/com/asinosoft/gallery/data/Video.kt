package com.asinosoft.gallery.data

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

data class Video(
    @PrimaryKey
    val id: UUID,
    val uri: Uri,
    val date: LocalDate,
    val time: LocalTime,
    val width: Int,
    val height: Int,
    val orientation: Int,
    val album: String?,
    val size: Long,
    val filename: String,
)
