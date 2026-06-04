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

@Composable
fun AlbumCategory.name(): String =
    if (":other" == name)
        stringResource(R.string.other)
    else
        name
