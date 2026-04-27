package com.asinosoft.gallery.data.storage

import android.net.Uri
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "storage")
data class Storage(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val type: StorageType = StorageType.LOCAL,
    val url: Uri? = null,
    val login: String? = null,
    val password: String? = null
) {
    fun withId(id: Long) = Storage(
        id = id,
        type = type,
        url = url,
        login = login,
        password = password
    )
}
