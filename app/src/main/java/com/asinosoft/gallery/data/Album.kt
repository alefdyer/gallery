package com.asinosoft.gallery.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.LocalDate

@Entity(
    tableName = "album",
    indices = [
        Index(value = ["categoryId", "name"], unique = true),
        Index(value = ["categoryId"])
    ],
    foreignKeys = [
        ForeignKey(
            entity = AlbumCategory::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL
        )
    ]
)
data class Album(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val categoryId: Long = AlbumCategory.OTHER.id,
    val count: Int = 0,
    val size: Long = 0,
    val date: LocalDate = LocalDate.now(),
    /** Альбом создан пользователем через «Новый альбом», а не автоматически из папки */
    val userCreated: Boolean = false
)

data class AlbumWithCover(
    @Embedded val album: Album,
    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: AlbumCategory
)
