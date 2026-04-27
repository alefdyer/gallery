package com.asinosoft.gallery.data.storage

import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.MediaDao
import com.asinosoft.gallery.data.MediaService
import javax.inject.Inject

class StorageService @Inject constructor(
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
    private val storageDao: StorageDao,
    private val storageProviderRegistry: StorageProviderRegistry,
    private val storageAuthProvider: StorageAuthProvider,
    private val mediaService: MediaService
) {
    suspend fun addStorage(storage: Storage) {
        storageDao.upsert(storage)
        storageAuthProvider.refresh()
        val provider = storageProviderRegistry.getStorageProvider(storage)
        mediaService.update(provider)
    }

    suspend fun deleteStorage(storage: Storage) {
        storageDao.delete(storage)
        mediaDao.deleteStorage(storage.id)
        albumDao.deleteEmptyAlbums()
        storageAuthProvider.refresh()
    }
}
