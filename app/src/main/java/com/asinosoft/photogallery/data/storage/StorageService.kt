package com.asinosoft.photogallery.data.storage

import android.util.Log
import com.asinosoft.photogallery.data.AlbumDao
import com.asinosoft.photogallery.data.MediaDao
import com.asinosoft.photogallery.data.MediaService
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

@Singleton
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
    private val fetchingTypesFlow = MutableStateFlow<Set<StorageType>>(emptySet())
    val fetchingTypes: StateFlow<Set<StorageType>> = fetchingTypesFlow
    private val fetchMutex = Mutex()

    /** Живёт дольше любого ViewModel — скан после подключения WebDAV не отменяется при уходе с экрана. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun checkStorage(storage: Storage): StorageCheckResult = withContext(Dispatchers.IO) {
        val provider = storageProviderRegistry.createStorageProvider(storage)
        provider.checkConnection()
    }

    suspend fun addStorage(storage: Storage): Storage {
        val insertedId = storageDao.upsert(storage)
        val saved = if (insertedId > 0) storage.withId(insertedId) else storage
        storageProviderRegistry.invalidate(saved.id)
        storageAuthProvider.refresh()
        // Не ждём полный обход в viewModelScope — иначе при закрытии настроек скан рвётся и остаётся 0.
        enqueueFetch(saved)
        return saved
    }

    suspend fun deleteStorage(storage: Storage) {
        storageDao.delete(storage)
        storageProviderRegistry.invalidate(storage.id)
        mediaDao.deleteStorage(storage.id)
        albumDao.deleteEmptyAlbums()
        storageAuthProvider.refresh()
    }

    fun enqueueFetch(storage: Storage) {
        scope.launch {
            runCatching { fetch(storage) }
                .onFailure { Log.e(TAG, "Fetch failed: $storage", it) }
        }
    }

    suspend fun fetch(storage: Storage) = fetchMutex.withLock {
        fetchingFlow.emit(true)
        fetchingTypesFlow.emit(fetchingTypesFlow.value + storage.type)
        try {
            val provider = storageProviderRegistry.getStorageProvider(storage.id)
            measureTimeMillis {
                Log.i(TAG, "Update storage: $storage")
                mediaService.update(provider)
            }.also {
                Log.i(TAG, "Updated in $it ms")
            }
        } finally {
            fetchingTypesFlow.emit(fetchingTypesFlow.value - storage.type)
            fetchingFlow.emit(fetchingTypesFlow.value.isNotEmpty())
        }
    }

    private companion object {
        const val TAG = "data"
    }
}
