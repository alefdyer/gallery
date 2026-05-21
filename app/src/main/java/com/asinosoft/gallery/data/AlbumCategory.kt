package com.asinosoft.gallery.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

@Entity(
    tableName = "album_category",
    indices = [Index(value = ["name"], unique = true)],
)
data class AlbumCategory(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String
) {
    companion object {
        const val OTHER = 1L
    }
}

data class CategoryWithAlbums(
    @Embedded val category: AlbumCategory,
    @Relation(
        parentColumn = "id",
        entityColumn = "categoryId"
    )
    val albums: List<AlbumWithCover>
)
