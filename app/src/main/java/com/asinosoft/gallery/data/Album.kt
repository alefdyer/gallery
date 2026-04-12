package com.asinosoft.gallery.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

@Entity(
    tableName = "album",
    indices = [Index(value = ["name"], unique = true)]
)
data class Album(
    @PrimaryKey
    val id: UUID,
    val name: String,
    val count: Int = 0,
    val size: Long = 0,
    val cover: String? = null,
    val date: LocalDate = LocalDate.now()
)
