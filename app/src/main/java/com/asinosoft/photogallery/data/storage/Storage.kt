package com.asinosoft.photogallery.data.storage

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.asinosoft.photogallery.R

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

    val title: String
        @Composable
        get() = when (type) {
            StorageType.LOCAL -> stringResource(R.string.phone)
            StorageType.DROPBOX -> stringResource(R.string.dropbox)
            StorageType.NEXTCLOUD -> stringResource(R.string.nextcloud)
            StorageType.WEBDAV -> stringResource(R.string.webdav)
            StorageType.YANDEX -> stringResource(R.string.yandex)
            StorageType.GOOGLE_DRIVE -> login?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.google_drive)

            StorageType.ONEDRIVE -> login?.takeIf { it.isNotBlank() }
                ?: stringResource(R.string.onedrive)
        }

    /** Стабильная подпись хранилища для имён альбомов (без Compose). */
    fun albumLabel(): String {
        val host = url?.host?.takeIf { it.isNotBlank() }
        val user = login?.takeIf { it.isNotBlank() }
        return when {
            host != null && user != null -> "$user@$host"
            host != null -> host
            user != null -> user
            else -> "${type.name.lowercase()}-$id"
        }
    }

    /** Заголовок категории на вкладке «Альбомы». */
    fun categoryTitle(context: Context): String = when (type) {
        StorageType.LOCAL -> context.getString(R.string.phone)
        StorageType.DROPBOX -> context.getString(R.string.dropbox)
        StorageType.NEXTCLOUD -> login?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.nextcloud)

        StorageType.WEBDAV -> context.getString(R.string.webdav)
        StorageType.YANDEX -> context.getString(R.string.yandex)
        StorageType.GOOGLE_DRIVE -> login?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.google_drive)

        StorageType.ONEDRIVE -> login?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.onedrive)
    }
}
