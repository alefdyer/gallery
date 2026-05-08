package com.asinosoft.gallery.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
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
    val coverId: Long? = null,
    val date: LocalDate = LocalDate.now()
)

data class AlbumWithCover(
    @Embedded val album: Album,
    @Relation(
        parentColumn = "coverId",
        entityColumn = "id"
    )
    val cover: Media? = null
)
