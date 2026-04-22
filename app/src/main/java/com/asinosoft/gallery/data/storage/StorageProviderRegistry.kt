package com.asinosoft.gallery.data.storage

import android.content.Context
import com.asinosoft.gallery.data.storage.local.LocalStorageProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageProviderRegistry
@Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun getStorageProvider(storage: Storage): StorageProvider = when (storage.type) {
        StorageType.LOCAL -> LocalStorageProvider(storage.id, context)
        StorageType.DROPBOX -> DropboxStorageProvider()
        StorageType.NEXTCLOUD -> NextCloudStorageProvider()
        StorageType.WEBDAV -> WebDavStorageProvider()
        StorageType.YANDEX -> YandexStorageProvider()
    }
}
