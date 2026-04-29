package com.asinosoft.gallery.data.storage

import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.MediaDao
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class StorageService @Inject constructor(
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
    private val storageDao: StorageDao,
    private val storageProviderRegistry: StorageProviderRegistry,
    private val storageAuthProvider: StorageAuthProvider
) {
    suspend fun checkStorage(storage: Storage): StorageCheckResult = withContext(Dispatchers.IO) {
        val provider = storageProviderRegistry.createStorageProvider(storage)
        provider.checkConnection()
    }

    suspend fun addStorage(storage: Storage): Storage = storage.withId(storageDao.upsert(storage))
        .also { storageAuthProvider.refresh() }

    suspend fun deleteStorage(storage: Storage) {
        storageDao.delete(storage)
        mediaDao.deleteStorage(storage.id)
        albumDao.deleteEmptyAlbums()
        storageAuthProvider.refresh()
    }
}
