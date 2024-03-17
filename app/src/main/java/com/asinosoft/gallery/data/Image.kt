package com.asinosoft.gallery.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate
import java.util.UUID

@Entity(tableName = "images")
data class Image(
    @PrimaryKey
    val path: String,
    val date: LocalDate,
    val width: Int,
    val height: Int,
    val uuid: String = UUID.randomUUID().toString()
)