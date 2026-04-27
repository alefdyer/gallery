package com.asinosoft.gallery.data.storage

import android.content.Context
import com.asinosoft.gallery.data.storage.local.LocalStorageProvider
import com.asinosoft.gallery.data.storage.nextcloud.NextCloudStorageProvider
import com.asinosoft.gallery.data.storage.webdav.WebDavStorageProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageProviderRegistry
@Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val cache = mutableMapOf<Long, StorageProvider>()

    fun getStorageProvider(storage: Storage): StorageProvider =
        cache.getOrPut(storage.id) { createStorageProvider(storage) }

    private fun createStorageProvider(storage: Storage): StorageProvider = when (storage.type) {
        StorageType.LOCAL -> LocalStorageProvider(storage, context)
        StorageType.DROPBOX -> DropboxStorageProvider(storage)
        StorageType.NEXTCLOUD -> NextCloudStorageProvider(storage)
        StorageType.WEBDAV -> WebDavStorageProvider(storage)
        StorageType.YANDEX -> YandexStorageProvider(storage)
    }
}
