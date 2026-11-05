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
    val password: String? = null,
    /** Epoch ms последнего успешного скана. */
    val lastFetchedAt: Long = 0L,
    /** Opaque cursor: OneDrive deltaLink / Dropbox cursor / Drive pageToken / DAV sync-token. */
    val syncCursor: String? = null,
) {
    fun withId(id: Long) = copy(id = id)

    val title: String
        @Composable
        get() = when (type) {
            StorageType.LOCAL -> stringResource(R.string.phone)
            StorageType.DROPBOX -> stringResource(R.string.dropbox)
            StorageType.NEXTCLOUD -> stringResource(R.string.nextcloud)
            StorageType.WEBDAV -> stringResource(R.string.webdav)
            StorageType.YANDEX -> stringResource(R.string.yandex)
            StorageType.GOOGLE_DRIVE -> stringResource(R.string.google_drive)

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

    /** Хост WebDAV без схемы и пути (для заголовка категории на «Альбомы»). */
    fun webDavServerLabel(): String? {
        val uri = url ?: return null
        val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
        val port = uri.port
        return if (port != -1 && port != 80 && port != 443) "$host:$port" else host
    }

    /** Заголовок категории на вкладке «Альбомы». */
    fun categoryTitle(context: Context): String = when (type) {
        StorageType.LOCAL -> context.getString(R.string.phone)
        StorageType.DROPBOX -> login?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.dropbox)
        StorageType.NEXTCLOUD -> login?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.nextcloud)

        StorageType.WEBDAV -> webDavServerLabel()
            ?: context.getString(R.string.webdav)
        StorageType.YANDEX -> context.getString(R.string.yandex)
        StorageType.GOOGLE_DRIVE -> login?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.google_drive)

        StorageType.ONEDRIVE -> login?.takeIf { it.isNotBlank() }
            ?: context.getString(R.string.onedrive)
    }

    /**
     * Автосинк при входе в приложение.
     * С курсором — дельта, не чаще 15 минут.
     * WebDAV и Nextcloud без курсора — сверка даты папки при каждом входе.
     */
    fun allowsAutoSync(): Boolean = when (type) {
        StorageType.LOCAL,
        StorageType.YANDEX,
            -> false

        StorageType.WEBDAV,
        StorageType.NEXTCLOUD,
        StorageType.DROPBOX,
        StorageType.GOOGLE_DRIVE,
        StorageType.ONEDRIVE,
            -> true
    }

    /** Настоящий sync-token. Метка «сервер не умеет sync-collection» курсором не считается. */
    fun hasSyncToken(): Boolean =
        !syncCursor.isNullOrBlank() && syncCursor != NO_SYNC_COLLECTION

    /** Без sync-collection: при входе и раскрытии сверяется дата папки, не список файлов. */
    fun usesFastFolderCheck(): Boolean = when (type) {
        StorageType.WEBDAV,
        StorageType.NEXTCLOUD,
            -> !hasSyncToken()

        else -> false
    }

    companion object {
        const val AUTO_SYNC_MIN_INTERVAL_MS = 15L * 60_000L

        /** Сервер один раз ответил, что sync-collection нет. Повторять REPORT не нужно. */
        const val NO_SYNC_COLLECTION = "nosync"
    }
}
