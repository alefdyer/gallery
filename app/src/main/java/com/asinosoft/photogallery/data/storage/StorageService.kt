package com.asinosoft.photogallery.data.storage

import android.content.Context
import android.util.Log
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.AlbumCategory
import com.asinosoft.photogallery.data.AlbumDao
import com.asinosoft.photogallery.data.MediaDao
import com.asinosoft.photogallery.data.MediaService
import com.asinosoft.photogallery.data.MessageBus
import com.asinosoft.photogallery.data.ThumbnailCache
import android.app.PendingIntent
import com.asinosoft.photogallery.data.storage.gdrive.GoogleDriveGalleryAuth
import com.asinosoft.photogallery.data.storage.gdrive.GoogleDriveReauthRequest
import com.google.android.gms.auth.api.identity.Identity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

@Singleton
class StorageService @Inject constructor(
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
    private val mediaService: MediaService,
    private val storageDao: StorageDao,
    private val storageProviderRegistry: StorageProviderRegistry,
    private val storageAuthProvider: StorageAuthProvider,
    @param:ApplicationContext private val context: Context,
) {
    private val fetchingFlow = MutableStateFlow(false)
    val isFetching: StateFlow<Boolean> = fetchingFlow
    private val fetchingTypesFlow = MutableStateFlow<Set<StorageType>>(emptySet())
    val fetchingTypes: StateFlow<Set<StorageType>> = fetchingTypesFlow
    private val fetchingStorageIdsFlow = MutableStateFlow<Set<Long>>(emptySet())
    val fetchingStorageIds: StateFlow<Set<Long>> = fetchingStorageIdsFlow

    /** Отдельный mutex на хранилище — локальный скан не блокирует облако. */
    private val fetchMutexes = mutableMapOf<Long, Mutex>()
    private val fetchingRefCounts = mutableMapOf<Long, Int>()
    private val fetchingTypesRefCounts = mutableMapOf<StorageType, Int>()

    /** Identity hasResolution — Activity должна показать PendingIntent (один на storage). */
    private val reauthInFlight = mutableSetOf<Long>()

    // Channel: при двух Activity (Main+Viewer) событие заберёт только один collector.
    private val driveReauthChannel = Channel<GoogleDriveReauthRequest>(Channel.CONFLATED)
    val driveReauthRequests: Flow<GoogleDriveReauthRequest> = driveReauthChannel.receiveAsFlow()

    /** Живёт дольше любого ViewModel — скан после подключения не отменяется при уходе с экрана. */
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
        enqueueFetch(saved)
        return saved
    }

    suspend fun deleteStorage(storage: Storage) = withContext(Dispatchers.IO) {
        val mediaIds = mediaDao.getIdsForStorage(storage.id)
        ThumbnailCache.deleteAll(context, mediaIds)
        mediaDao.deleteStorage(storage.id)
        albumDao.deleteEmptyAlbums()
        albumDao.findCategoryByName(AlbumCategory.storageCategoryName(storage.id))
            ?.let { albumDao.deleteCategory(it.id) }
        if (storage.type == StorageType.WEBDAV) {
            albumDao.findCategoryByName(AlbumCategory.WEBDAV_NAME)?.let { legacy ->
                albumDao.deleteCategory(legacy.id)
            }
        }
        albumDao.deleteEmptyCategories()
        storageDao.delete(storage)
        storageProviderRegistry.invalidate(storage.id)
        storageAuthProvider.refresh()
    }

    fun enqueueFetch(storage: Storage) {
        scope.launch {
            runCatching { fetch(storage) }
                .onFailure { ex ->
                    Log.e(TAG, "Fetch failed: $storage", ex)
                    val label = storage.categoryTitle(context)
                    MessageBus.emit(
                        context.getString(R.string.storage_refresh_failed, label)
                    )
                }
        }
    }

    suspend fun fetch(storage: Storage) {
        beginFetching(storage)
        try {
            val mutex = synchronized(fetchMutexes) {
                fetchMutexes.getOrPut(storage.id) { Mutex() }
            }
            mutex.withLock {
                var fresh = storageDao.getStorageById(storage.id)
                fresh = refreshGoogleDriveAccessToken(fresh) ?: return@withLock
                storageProviderRegistry.invalidate(fresh.id)
                val provider = storageProviderRegistry.getStorageProvider(fresh.id)
                measureTimeMillis {
                    Log.i(TAG, "Update storage: ${fresh.type} id=${fresh.id}")
                    mediaService.update(provider)
                }.also {
                    Log.i(TAG, "Updated ${fresh.type} id=${fresh.id} in $it ms")
                }
            }
        } finally {
            endFetching(storage)
        }
    }

    /** Обновить OAuth перед сканом/открытием файла. null = ждём UI consent. */
    suspend fun ensureFreshAuth(storageId: Long): Storage? {
        val storage = runCatching { storageDao.getStorageById(storageId) }.getOrNull()
            ?: return null
        if (storage.type != StorageType.GOOGLE_DRIVE) return storage
        return refreshGoogleDriveAccessToken(storage)
    }

    /** Заголовки для плееров без OkHttp interceptor (LibVLC). */
    suspend fun httpAuthHeaders(storageId: Long): Map<String, String> =
        withContext(Dispatchers.IO) {
            val storage = runCatching { storageDao.getStorageById(storageId) }.getOrNull()
                ?: return@withContext emptyMap()
            val password = storage.password ?: return@withContext emptyMap()
            when (storage.type) {
                StorageType.GOOGLE_DRIVE,
                StorageType.DROPBOX,
                StorageType.ONEDRIVE,
                    -> mapOf("Authorization" to "Bearer $password")

                StorageType.YANDEX -> mapOf("Authorization" to "OAuth $password")
                StorageType.WEBDAV,
                StorageType.NEXTCLOUD,
                    -> {
                    val login = storage.login ?: return@withContext emptyMap()
                    mapOf(
                        "Authorization" to okhttp3.Credentials.basic(login, password)
                    )
                }

                StorageType.LOCAL -> emptyMap()
            }
        }

    /** После экрана Google — сохранить токен и при необходимости продолжить скан. */
    suspend fun applyGoogleDriveToken(
        storageId: Long,
        token: String,
        retryFetch: Boolean,
    ) = withContext(Dispatchers.IO) {
        clearReauthInFlight(storageId)
        val storage = runCatching { storageDao.getStorageById(storageId) }.getOrNull()
            ?: return@withContext
        if (storage.type != StorageType.GOOGLE_DRIVE) return@withContext
        val updated = storage.copy(password = token)
        storageDao.upsert(updated)
        storageProviderRegistry.invalidate(storageId)
        storageAuthProvider.refresh()
        Log.i(TAG, "Applied Google Drive token for storage $storageId")
        if (retryFetch) enqueueFetch(updated)
    }

    fun cancelGoogleDriveReauth(storageId: Long) {
        clearReauthInFlight(storageId)
    }

    /**
     * Access token Drive живёт ~1 час. Тихо запрашиваем новый через Identity;
     * если нужен UI — эмитим PendingIntent для Activity.
     * @return null — ждём consent UI, скан/загрузку пропускаем.
     */
    private suspend fun refreshGoogleDriveAccessToken(storage: Storage): Storage? {
        if (storage.type != StorageType.GOOGLE_DRIVE) return storage
        return withContext(Dispatchers.IO) {
            try {
                val result = Identity.getAuthorizationClient(context)
                    .authorize(GoogleDriveGalleryAuth.authorizationRequest())
                    .await()
                if (result.hasResolution()) {
                    val pending = result.pendingIntent
                    if (pending != null) {
                        offerDriveReauth(storage.id, pending)
                    } else {
                        MessageBus.emit(
                            context.getString(
                                R.string.storage_reconnect_required,
                                storage.categoryTitle(context)
                            )
                        )
                    }
                    return@withContext null
                }
                val token = result.accessToken?.takeIf { it.isNotBlank() }
                    ?: return@withContext storage
                if (token == storage.password) return@withContext storage
                val updated = storage.copy(password = token)
                storageDao.upsert(updated)
                Log.i(TAG, "Refreshed Google Drive access token for storage ${storage.id}")
                updated
            } catch (ex: Throwable) {
                Log.w(TAG, "Silent Drive token refresh failed", ex)
                // Не рвём открытие файла: пробуем токен из БД; UI-reauth — если нет токена.
                if (!storage.password.isNullOrBlank()) {
                    return@withContext storage
                }
                MessageBus.emit(
                    context.getString(
                        R.string.storage_reconnect_required,
                        storage.categoryTitle(context)
                    )
                )
                null
            }
        }
    }

    private fun offerDriveReauth(storageId: Long, pendingIntent: PendingIntent) {
        val shouldEmit = synchronized(reauthInFlight) { reauthInFlight.add(storageId) }
        if (!shouldEmit) return
        val sent = driveReauthChannel.trySend(
            GoogleDriveReauthRequest(storageId, pendingIntent)
        )
        if (sent.isFailure) clearReauthInFlight(storageId)
    }

    private fun clearReauthInFlight(storageId: Long) {
        synchronized(reauthInFlight) { reauthInFlight.remove(storageId) }
    }

    private fun beginFetching(storage: Storage) {
        synchronized(fetchingRefCounts) {
            fetchingRefCounts[storage.id] = (fetchingRefCounts[storage.id] ?: 0) + 1
            fetchingTypesRefCounts[storage.type] =
                (fetchingTypesRefCounts[storage.type] ?: 0) + 1
            publishFetchingLocked()
        }
    }

    private fun endFetching(storage: Storage) {
        synchronized(fetchingRefCounts) {
            val nextId = (fetchingRefCounts[storage.id] ?: 1) - 1
            if (nextId <= 0) fetchingRefCounts.remove(storage.id)
            else fetchingRefCounts[storage.id] = nextId

            val nextType = (fetchingTypesRefCounts[storage.type] ?: 1) - 1
            if (nextType <= 0) fetchingTypesRefCounts.remove(storage.type)
            else fetchingTypesRefCounts[storage.type] = nextType

            publishFetchingLocked()
        }
    }

    private fun publishFetchingLocked() {
        fetchingStorageIdsFlow.value = fetchingRefCounts.keys.toSet()
        fetchingTypesFlow.value = fetchingTypesRefCounts.keys.toSet()
        fetchingFlow.value = fetchingRefCounts.isNotEmpty()
    }

    private companion object {
        const val TAG = "data"
    }
}
