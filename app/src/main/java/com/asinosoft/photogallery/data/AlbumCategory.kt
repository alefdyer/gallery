package com.asinosoft.photogallery.data

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.asinosoft.photogallery.R

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
        const val WEBDAV_NAME = ":webdav"
        const val STORAGE_PREFIX = ":storage:"

        fun storageCategoryName(storageId: Long): String = "$STORAGE_PREFIX$storageId"
    }
}

fun AlbumCategory.storageIdOrNull(): Long? =
    name.removePrefix(AlbumCategory.STORAGE_PREFIX)
        .toLongOrNull()
        .takeIf { name.startsWith(AlbumCategory.STORAGE_PREFIX) }

fun AlbumCategory.isStorageCategory(): Boolean = storageIdOrNull() != null

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
    when {
        name == ":other" -> stringResource(R.string.other)
        name == AlbumCategory.MESSENGERS_NAME -> stringResource(R.string.messengers)
        name == AlbumCategory.WEBDAV_NAME -> stringResource(R.string.storage_type_webdav)
        // Не сырой :storage:id — в шапке альбома «Хранилище» / Storage.
        isStorageCategory() -> stringResource(R.string.storage)
        else -> name
    }
