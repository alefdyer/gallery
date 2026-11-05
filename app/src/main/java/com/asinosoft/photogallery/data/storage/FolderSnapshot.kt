package com.asinosoft.photogallery.data.storage

import androidx.room.Entity

/** Отпечаток списка одной папки. Совпадение значит, что альбом этой папки не переписываем. */
@Entity(
    tableName = "folder_snapshot",
    primaryKeys = ["storageId", "path"],
)
data class FolderSnapshot(
    val storageId: Long = 0,
    val path: String,
    val fingerprint: String,
    /** ETag и дата самой папки. Пустая строка — ещё не запоминали. */
    val dirStamp: String = "",
    /** URL вложенных папок, по одному на строку. */
    val childUrls: String = "",
)
