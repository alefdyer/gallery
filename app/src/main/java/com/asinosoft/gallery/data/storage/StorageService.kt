package com.asinosoft.gallery.data.storage

import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.MediaDao
import com.asinosoft.gallery.data.MediaService
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

class StorageService @Inject constructor(
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
    private val mediaService: MediaService,
    private val storageDao: StorageDao,
    private val storageProviderRegistry: StorageProviderRegistry,
    private val storageAuthProvider: StorageAuthProvider
) {
    private val fetchingFlow = MutableStateFlow(false)
    val isFetching: StateFlow<Boolean> = fetchingFlow

    suspend fun checkStorage(storage: Storage): StorageCheckResult = withContext(Dispatchers.IO) {
        val provider = storageProviderRegistry.createStorageProvider(storage)
        provider.checkConnection()
    }

    suspend fun addStorage(storage: Storage): Storage = storage.withId(storageDao.upsert(storage))
        .also {
            storageAuthProvider.refresh()
            fetch(it)
        }

    suspend fun deleteStorage(storage: Storage) {
        storageDao.delete(storage)
        mediaDao.deleteStorage(storage.id)
        albumDao.deleteEmptyAlbums()
        storageAuthProvider.refresh()
    }

    suspend fun fetch(storage: Storage) {
        fetchingFlow.emit(true)
        try {
            val provider = storageProviderRegistry.getStorageProvider(storage.id)
            mediaService.update(provider)
        } finally {
            fetchingFlow.emit(false)
        }
    }
}
