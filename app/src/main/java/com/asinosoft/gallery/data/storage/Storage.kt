package com.asinosoft.gallery.data.storage

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.asinosoft.gallery.R

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

    @Composable
    fun title(): String = when(type) {
        StorageType.LOCAL -> stringResource(R.string.phone)
        StorageType.DROPBOX -> stringResource(R.string.dropbox)
        StorageType.NEXTCLOUD -> stringResource(R.string.nextcloud)
        StorageType.WEBDAV -> stringResource(R.string.webdav)
        StorageType.YANDEX -> stringResource(R.string.yandex)
    }
}
