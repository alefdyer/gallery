package com.asinosoft.gallery.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity(
    tableName = "album",
    indices = [Index(value = ["name"], unique = true)]
)
data class Album(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val count: Int = 0,
    val size: Long = 0,
    val cover: String? = null,
    val date: LocalDate = LocalDate.now()
)
