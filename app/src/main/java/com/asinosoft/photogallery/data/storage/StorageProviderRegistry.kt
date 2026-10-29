package com.asinosoft.photogallery.data.storage

import android.content.Context
import com.asinosoft.photogallery.data.storage.dropbox.DropboxStorageProvider
import com.asinosoft.photogallery.data.storage.gdrive.GoogleDriveStorageProvider
import com.asinosoft.photogallery.data.storage.local.LocalStorageProvider
import com.asinosoft.photogallery.data.storage.nextcloud.NextCloudStorageProvider
import com.asinosoft.photogallery.data.storage.onedrive.OneDriveStorageProvider
import com.asinosoft.photogallery.data.storage.webdav.WebDavStorageProvider
import com.asinosoft.photogallery.data.storage.yandex.YandexStorageProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StorageProviderRegistry
@Inject constructor(
    val storageDao: StorageDao,
    @param:ApplicationContext private val context: Context
) {
    private val cache = mutableMapOf<Long, StorageProvider>()

    suspend fun getStorageProvider(storageId: Long): StorageProvider = cache.getOrPut(storageId) {
        val storage = storageDao.getStorageById(storageId)
        createStorageProvider(storage)
    }

    fun invalidate(storageId: Long) {
        cache.remove(storageId)
    }

    fun createStorageProvider(storage: Storage): StorageProvider = when (storage.type) {
        StorageType.LOCAL -> LocalStorageProvider(storage, context)
        StorageType.DROPBOX -> DropboxStorageProvider(storage, context)
        StorageType.NEXTCLOUD -> NextCloudStorageProvider(storage)
        StorageType.WEBDAV -> WebDavStorageProvider(storage)
        StorageType.YANDEX -> YandexStorageProvider(storage)
        StorageType.GOOGLE_DRIVE -> GoogleDriveStorageProvider(storage)
        StorageType.ONEDRIVE -> OneDriveStorageProvider(storage)
    }
}
