package com.asinosoft.gallery.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.asinosoft.gallery.R

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
        val OTHER = AlbumCategory(1, ":other")
        const val MESSENGERS_NAME = ":messengers"
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

fun AlbumCategory.isSystem(): Boolean = name.startsWith(":")

@Composable
fun AlbumCategory.name(): String =
    when (name) {
        ":other" -> stringResource(R.string.other)
        AlbumCategory.MESSENGERS_NAME -> stringResource(R.string.messengers)
        else -> name
    }
