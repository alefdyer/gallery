package com.asinosoft.gallery.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

@Entity
data class Album(
    @PrimaryKey
    val name: String,
    val count: Int,
    val size: Long,
    val cover: String,
    val date: LocalDate,
)
