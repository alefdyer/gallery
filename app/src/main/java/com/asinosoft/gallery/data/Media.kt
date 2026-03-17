package com.asinosoft.gallery.data

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime
import java.util.UUID

@Entity(
    indices = [
        Index(value = ["uri"], unique = true),
    ],
)
data class Media(
    @PrimaryKey
    val id: UUID,
    val uri: Uri,
    val date: LocalDate,
    val time: LocalTime,
    val album: String?,
    val size: Long,
    val filename: String,
    val mimeType: String,
    val image: Image? = null,
    val video: Video? = null,
)
