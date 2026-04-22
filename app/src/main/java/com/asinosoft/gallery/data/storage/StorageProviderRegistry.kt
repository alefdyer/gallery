package com.asinosoft.gallery.data.storage

import android.content.Context
import android.net.Uri
import com.asinosoft.gallery.data.local.LocalStorageProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageProviderRegistry
@Inject constructor(
    private val accountDao: StorageDao,
    @param:ApplicationContext private val context: Context
) {
    suspend fun resolveEnabledProviders(): List<StorageProvider> = accountDao.getStorages().map {
        when (it.type) {
            StorageType.LOCAL -> LocalStorageProvider(it, context)
            StorageType.DROPBOX -> DropboxStorageProvider()
            StorageType.NEXTCLOUD -> NextCloudStorageProvider()
            StorageType.WEBDAV -> WebDavStorageProvider()
            StorageType.YANDEX -> YandexStorageProvider()
        }
    }
}
