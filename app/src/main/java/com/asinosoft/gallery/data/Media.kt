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
    val id: Long = 0,
    val uri: Uri? = null,
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now(),
    val path: String = "",
    val size: Long = -1,
    val filename: String = "",
    val mimeType: String = "",
    val storageId: Long = 0,
    val storageItemId: String = "",
    val thumbnail: Uri? = null,
    val image: Image? = null,
    val video: Video? = null
)
