package com.asinosoft.photogallery.data

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
    val coverId: Long? = null,
    val date: LocalDate = LocalDate.now(),
    /** Альбом создан пользователем через «Новый альбом», а не автоматически из папки */
    val userCreated: Boolean = false
)

/**
 * Имя альбома из родительского path файла.
 * Пустой / корневой path → «Главная · &lt;хранилище&gt;» ([rootAlbumName]).
 */
fun albumNameFromPath(path: String, rootAlbumName: String): String =
    path.split('/').lastOrNull { it.isNotEmpty() } ?: rootAlbumName

/** Родительская папка; если файл в корне [rootPath] — пустая строка. */
fun mediaParentPath(itemPath: String, rootPath: String): String {
    val parent = itemPath.dropLastWhile { it != '/' }
    val parentNorm = parent.trimEnd('/')
    val rootNorm = rootPath.trimEnd('/')
    return if (parentNorm.isEmpty() || parentNorm == rootNorm) "" else parent
}

data class AlbumWithCover(
    @Embedded val album: Album,
    @Relation(
        parentColumn = "coverId",
        entityColumn = "id"
    )
    val cover: Media? = null,
    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: AlbumCategory
)
