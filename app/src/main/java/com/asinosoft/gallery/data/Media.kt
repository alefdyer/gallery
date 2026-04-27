package com.asinosoft.gallery.data

import android.net.Uri
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.time.LocalTime

@Entity(
    tableName = "media",
    indices = [
        Index(value = ["storageId", "storageItemId"], unique = true)
    ]
)
data class Media(
    @PrimaryKey(autoGenerate = true)
    val id: Long,
    val uri: Uri,
    val date: LocalDate,
    val time: LocalTime,
    val path: String,
    val size: Long,
    val filename: String,
    val mimeType: String,
    val storageId: Long,
    val storageItemId: String,
    val image: Image? = null,
    val video: Video? = null
)
