package com.asinosoft.gallery.data.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "storage")
data class Storage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: StorageType,
    val name: String,
    val url: String?,
    val username: String?,
    val secret: String?,
    val rootPath: String?
)
