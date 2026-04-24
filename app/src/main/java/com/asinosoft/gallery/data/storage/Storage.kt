package com.asinosoft.gallery.data.storage

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "storage")
data class Storage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: StorageType = StorageType.LOCAL,
    val name: String = "",
    val url: String? = null,
    val username: String? = null,
    val secret: String? = null,
    val rootPath: String? = null
)
