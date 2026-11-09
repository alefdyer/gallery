package com.asinosoft.photogallery.data.storage

import android.accounts.Account
import android.app.PendingIntent
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.AlbumCategory
import com.asinosoft.photogallery.data.AlbumDao
import com.asinosoft.photogallery.data.MediaDao
import com.asinosoft.photogallery.data.MediaService
import com.asinosoft.photogallery.data.ThumbnailCache
import com.asinosoft.photogallery.data.storage.dropbox.DropboxApi
import com.asinosoft.photogallery.data.storage.gdrive.GoogleDriveGalleryAuth
import com.asinosoft.photogallery.data.storage.gdrive.GoogleDriveReauthRequest
import com.asinosoft.photogallery.data.storage.gdrive.GoogleDriveStorageProvider
import com.asinosoft.photogallery.data.storage.onedrive.OneDriveApi
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
import kotlinx.coroutines.flow.first
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
    private val folderSnapshotDao: FolderSnapshotDao,
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

    /** Нужно переподключить / скан упал — жёлтая ! на месте крутилки. */
    private val attentionStorageIdsFlow = MutableStateFlow<Set<Long>>(emptySet())
    val attentionStorageIds: StateFlow<Set<Long>> = attentionStorageIdsFlow
    private val attentionMessages = mutableMapOf<Long, String>()
    /** Помечены только из‑за офлайна — снимаем, как только сеть снова есть. */
    private val offlineAttentionIds = mutableSetOf<Long>()
    /** Нужен повторный OAuth («Войти» в «Сеть»). */
    private val reauthRequiredIdSet = mutableSetOf<Long>()
    private val reauthRequiredIdsFlow = MutableStateFlow<Set<Long>>(emptySet())
    val reauthRequiredIds: StateFlow<Set<Long>> = reauthRequiredIdsFlow

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
        var toSave = storage
        if (toSave.type == StorageType.DROPBOX &&
            toSave.login.isNullOrBlank() &&
            !toSave.password.isNullOrBlank()
        ) {
            toSave = toSave.copy(login = DropboxApi.fetchEmail(toSave.password!!))
        }
        val oauthRefresh = when (toSave.type) {
            StorageType.DROPBOX -> DropboxApi.takePendingRefreshToken()
            StorageType.ONEDRIVE -> OneDriveApi.takePendingRefreshToken()
            else -> null
        }
        val insertedId = storageDao.upsert(toSave)
        val saved = if (insertedId > 0) toSave.withId(insertedId) else toSave
        if (!oauthRefresh.isNullOrBlank()) {
            storageDao.putRefreshToken(saved.id, oauthRefresh)
            Log.i(TAG, "Saved ${toSave.type} refresh token for storage ${saved.id}")
        }
        clearStorageAttention(saved.id)
        storageProviderRegistry.invalidate(saved.id)
        storageAuthProvider.refresh()
        enqueueFetch(saved, force = true, reason = SyncReason.Manual)
        return saved
    }

    suspend fun deleteStorage(storage: Storage) = withContext(Dispatchers.IO) {
        val mediaIds = mediaDao.getIdsForStorage(storage.id)
        ThumbnailCache.deleteAll(context, mediaIds)
        mediaDao.deleteStorage(storage.id)
        folderSnapshotDao.deleteForStorage(storage.id)
        albumDao.deleteEmptyAlbums()
        // categoryId у album NOT NULL + FK SET_NULL → DELETE категории падает.
        // Сначала явно сносим альбомы категории хранилища.
        albumDao.findCategoryByName(AlbumCategory.storageCategoryName(storage.id))
            ?.let { cat ->
                albumDao.deleteAlbumsByCategory(cat.id)
                albumDao.deleteCategory(cat.id)
            }
        if (storage.type == StorageType.WEBDAV) {
            albumDao.findCategoryByName(AlbumCategory.WEBDAV_NAME)?.let { legacy ->
                albumDao.deleteAlbumsByCategory(legacy.id)
                albumDao.deleteCategory(legacy.id)
            }
        }
        albumDao.deleteEmptyCategories()
        storageDao.delete(storage)
        storageProviderRegistry.invalidate(storage.id)
        storageAuthProvider.refresh()
    }

    fun enqueueFetch(
        storage: Storage,
        force: Boolean = false,
        reason: SyncReason = SyncReason.Manual,
    ) {
        scope.launch {
            runCatching { fetch(storage, force = force, reason = reason) }
                .onFailure { ex ->
                    Log.e(TAG, "Fetch failed: ${storage.type} id=${storage.id} reason=$reason", ex)
                    val label = storage.categoryTitle(context)
                    val authExpired = isAuthExpiredFailure(ex)
                    markStorageAttention(
                        storage.id,
                        context.getString(
                            if (authExpired) R.string.storage_reconnect_required
                            else R.string.storage_refresh_failed,
                            label,
                        ),
                        reauth = authExpired,
                    )
                }
        }
    }

    fun attentionMessage(storageId: Long): String? = synchronized(attentionMessages) {
        attentionMessages[storageId]
    }

    /** Показать «Войти» в «Сеть»: протухший OAuth, нет токена или нет refresh. */
    fun needsSignIn(storage: Storage): Boolean {
        if (storage.type == StorageType.LOCAL) return false
        if (storage.password.isNullOrBlank()) return true
        if (storage.type == StorageType.GOOGLE_DRIVE && storage.login.isNullOrBlank()) return true
        synchronized(attentionMessages) {
            if (storage.id in reauthRequiredIdSet) return true
        }
        if (storage.type == StorageType.DROPBOX || storage.type == StorageType.ONEDRIVE) {
            return storageDao.getRefreshToken(storage.id).isNullOrBlank()
        }
        return false
    }

    private fun markStorageAttention(
        storageId: Long,
        message: String,
        offline: Boolean = false,
        reauth: Boolean = false,
    ) {
        synchronized(attentionMessages) {
            attentionMessages[storageId] = message
            if (offline) offlineAttentionIds.add(storageId)
            else offlineAttentionIds.remove(storageId)
            if (reauth) reauthRequiredIdSet.add(storageId)
            else reauthRequiredIdSet.remove(storageId)
            attentionStorageIdsFlow.value = attentionMessages.keys.toSet()
            reauthRequiredIdsFlow.value = reauthRequiredIdSet.toSet()
        }
    }

    private fun clearStorageAttention(storageId: Long) {
        synchronized(attentionMessages) {
            offlineAttentionIds.remove(storageId)
            reauthRequiredIdSet.remove(storageId)
            reauthRequiredIdsFlow.value = reauthRequiredIdSet.toSet()
            if (attentionMessages.remove(storageId) != null) {
                attentionStorageIdsFlow.value = attentionMessages.keys.toSet()
            }
        }
    }

    /** Сеть снова есть — убрать ! от офлайна (синк мог скипнуться по интервалу). */
    private fun clearOfflineAttentionIfOnline(storageId: Long) {
        if (!isNetworkAvailable()) return
        synchronized(attentionMessages) {
            if (storageId !in offlineAttentionIds) return
            offlineAttentionIds.remove(storageId)
            if (attentionMessages.remove(storageId) != null) {
                attentionStorageIdsFlow.value = attentionMessages.keys.toSet()
            }
        }
    }

    suspend fun fetch(
        storage: Storage,
        force: Boolean = false,
        reason: SyncReason = SyncReason.Manual,
    ) {
        if (reason == SyncReason.AppOpen && !storage.allowsAutoSync()) {
            Log.d(TAG, "Skip AppOpen sync id=${storage.id} type=${storage.type}")
            return
        }
        if (skipForInterval(storage, force, reason)) {
            Log.d(
                TAG,
                "Skip fresh sync id=${storage.id} reason=$reason age=${System.currentTimeMillis() - storage.lastFetchedAt}ms",
            )
            // Иначе ! после краткого офлайна висит, пока не пройдёт полный sync.
            clearOfflineAttentionIfOnline(storage.id)
            return
        }
        // Без сети не крутим спиннер — сразу жёлтая ! у категории облака.
        if (storage.type != StorageType.LOCAL && !isNetworkAvailable()) {
            markStorageAttention(
                storage.id,
                context.getString(
                    R.string.storage_offline,
                    storage.categoryTitle(context),
                ),
                offline = true,
            )
            Log.d(TAG, "Skip sync offline id=${storage.id} type=${storage.type}")
            return
        }
        // Явное «Обновить» / сеть есть — сразу убрать залипшую ! (в т.ч. старую до offline-флага).
        if (force || reason == SyncReason.Manual) {
            clearStorageAttention(storage.id)
        } else {
            clearOfflineAttentionIfOnline(storage.id)
        }

        beginFetching(storage)
        try {
            val mutex = synchronized(fetchMutexes) {
                fetchMutexes.getOrPut(storage.id) { Mutex() }
            }
            mutex.withLock {
                var fresh = storageDao.getStorageById(storage.id)
                if (skipForInterval(fresh, force, reason)) {
                    Log.d(TAG, "Skip fresh sync (locked) id=${fresh.id} reason=$reason")
                    return@withLock
                }
                if (reason == SyncReason.AppOpen && !fresh.allowsAutoSync()) {
                    return@withLock
                }
                fresh = refreshOAuthAccessToken(fresh) ?: run {
                    if (attentionMessage(storage.id) == null) {
                        markStorageAttention(
                            storage.id,
                            context.getString(
                                R.string.storage_reconnect_required,
                                storage.categoryTitle(context),
                            ),
                            reauth = true,
                        )
                    }
                    return@withLock
                }
                storageProviderRegistry.invalidate(fresh.id)
                val provider = storageProviderRegistry.getStorageProvider(fresh.id)
                (provider as? FolderListingScan)?.setFastStampCheck(reason != SyncReason.Manual)
                var newCursor: String? = null
            measureTimeMillis {
                    Log.i(
                        TAG,
                        "Update storage: ${fresh.type} id=${fresh.id} reason=$reason " +
                                "force=$force cursor=${fresh.hasSyncToken()} " +
                                "fast=${reason != SyncReason.Manual}",
                    )
                    newCursor = mediaService.update(provider)
            }.also {
                    Log.i(TAG, "Updated ${fresh.type} id=${fresh.id} in $it ms")
                }
                storageDao.upsert(
                    fresh.copy(
                        lastFetchedAt = System.currentTimeMillis(),
                        syncCursor = newCursor,
                    )
                )
                clearStorageAttention(fresh.id)
            }
        } finally {
            endFetching(storage)
        }
    }

    /**
     * 15 минут — автосинк при входе для хранилищ с дельтой или sync-collection.
     * Без курсора вход и раскрытие сверяют дату папки сразу.
     * «Обновить» всегда запрашивает список файлов.
     */
    private fun skipForInterval(storage: Storage, force: Boolean, reason: SyncReason): Boolean {
        if (force || reason == SyncReason.Manual || reason == SyncReason.EnterCategory) return false
        if (reason == SyncReason.AppOpen && storage.usesFastFolderCheck()) return false
        if (storage.lastFetchedAt <= 0L) return false
        return System.currentTimeMillis() - storage.lastFetchedAt < Storage.AUTO_SYNC_MIN_INTERVAL_MS
    }

    /** Обновить OAuth перед сканом/открытием файла. null = ждём UI consent. */
    suspend fun ensureFreshAuth(storageId: Long): Storage? {
        val storage = runCatching { storageDao.getStorageById(storageId) }.getOrNull()
            ?: return null
        return refreshOAuthAccessToken(storage)
    }

    /** Drive / Dropbox / OneDrive: свежий access token. null только если Drive ждёт UI consent. */
    private suspend fun refreshOAuthAccessToken(storage: Storage): Storage? = when (storage.type) {
        StorageType.GOOGLE_DRIVE -> refreshGoogleDriveAccessToken(storage)
        StorageType.DROPBOX -> refreshDropboxAccessToken(storage)
        StorageType.ONEDRIVE -> refreshOneDriveAccessToken(storage)
        else -> storage
    }

    /**
     * Dropbox access ~4 часа. Refresh token живёт долго — обновляем перед синком/файлом.
     * Старые подключения без refresh token: оставляем access, пока не перелогинятся.
     */
    private suspend fun refreshDropboxAccessToken(storage: Storage): Storage {
        if (storage.type != StorageType.DROPBOX) return storage
        val refresh = storageDao.getRefreshToken(storage.id)
        if (refresh.isNullOrBlank()) {
            Log.d(TAG, "Dropbox ${storage.id}: no refresh token yet — using stored access token")
            return storage
        }
        val tokens = DropboxApi.refreshAccessToken(refresh)
        if (tokens == null) {
            // Access протух, refresh не прошёл — нужен повторный вход в «Сеть».
            Log.w(TAG, "Dropbox ${storage.id}: refresh failed — reauth required")
            markStorageAttention(
                storage.id,
                context.getString(
                    R.string.storage_reconnect_required,
                    storage.categoryTitle(context),
                ),
                reauth = true,
            )
            return storage
        }
        if (tokens.accessToken == storage.password &&
            (tokens.refreshToken.isNullOrBlank() || tokens.refreshToken == refresh)
        ) {
            return storage
        }
        val updated = storage.copy(password = tokens.accessToken)
        storageDao.upsert(updated)
        if (!tokens.refreshToken.isNullOrBlank()) {
            storageDao.putRefreshToken(storage.id, tokens.refreshToken)
        }
        storageProviderRegistry.invalidate(storage.id)
        Log.i(TAG, "Refreshed Dropbox access token for storage ${storage.id}")
        return updated
    }

    /** OneDrive access ~1 час. Без refresh token — один повторный вход. */
    private suspend fun refreshOneDriveAccessToken(storage: Storage): Storage {
        if (storage.type != StorageType.ONEDRIVE) return storage
        val refresh = storageDao.getRefreshToken(storage.id)
        if (refresh.isNullOrBlank()) {
            Log.d(TAG, "OneDrive ${storage.id}: no refresh token yet — using stored access token")
            return storage
        }
        val tokens = OneDriveApi.refreshAccessToken(refresh)
        if (tokens == null) {
            Log.w(TAG, "OneDrive ${storage.id}: refresh failed — reauth required")
            markStorageAttention(
                storage.id,
                context.getString(
                    R.string.storage_reconnect_required,
                    storage.categoryTitle(context),
                ),
                reauth = true,
            )
            return storage
        }
        if (tokens.accessToken == storage.password &&
            (tokens.refreshToken.isNullOrBlank() || tokens.refreshToken == refresh)
        ) {
            return storage
        }
        val updated = storage.copy(password = tokens.accessToken)
        storageDao.upsert(updated)
        if (!tokens.refreshToken.isNullOrBlank()) {
            storageDao.putRefreshToken(storage.id, tokens.refreshToken)
        }
        storageProviderRegistry.invalidate(storage.id)
        Log.i(TAG, "Refreshed OneDrive access token for storage ${storage.id}")
        return updated
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
        val email = storage.login?.takeIf { it.isNotBlank() }
            ?: GoogleDriveStorageProvider.fetchEmail(token)
        val updated = storage.copy(password = token, login = email ?: storage.login)
        storageDao.upsert(updated)
        storageProviderRegistry.invalidate(storageId)
        storageAuthProvider.refresh()
        clearStorageAttention(storageId)
        Log.i(TAG, "Applied Google Drive token for storage $storageId")
        if (retryFetch) enqueueFetch(updated, force = true, reason = SyncReason.Manual)
    }

    /** Старые Dropbox без login — email через users/get_current_account. */
    suspend fun backfillMissingDropboxEmails() = withContext(Dispatchers.IO) {
        val missing = storageDao.getStorages().first().filter {
            it.type == StorageType.DROPBOX &&
                    it.login.isNullOrBlank() &&
                    !it.password.isNullOrBlank()
        }
        for (storage in missing) {
            val email = DropboxApi.fetchEmail(storage.password!!) ?: continue
            storageDao.upsert(storage.copy(login = email))
            Log.i(TAG, "Backfilled Dropbox email for storage ${storage.id}")
        }
    }

    /** Старые Drive без login — подпись email для вкладки «Сеть». */
    suspend fun backfillMissingGoogleDriveEmails() = withContext(Dispatchers.IO) {
        val all = storageDao.getStorages().first()
        val missing = all.filter {
            it.type == StorageType.GOOGLE_DRIVE && it.login.isNullOrBlank()
        }
        if (missing.isEmpty()) return@withContext

        val usedEmails = all
            .filter { it.type == StorageType.GOOGLE_DRIVE }
            .mapNotNull { it.login?.lowercase()?.takeIf(String::isNotBlank) }
            .toMutableSet()
        val googleAccounts = android.accounts.AccountManager.get(context)
            .getAccountsByType("com.google")
            .toList()

        for (storage in missing) {
            var token = storage.password?.takeIf { it.isNotBlank() }
            var email = token?.let { GoogleDriveStorageProvider.fetchEmail(it) }

            // 1) API по сохранённому токену
            // 2) единственный Google-аккаунт на устройстве, ещё не привязанный к другому Drive
            // 3) silent authorize по каждому свободному аккаунту
            if (email.isNullOrBlank()) {
                val free = googleAccounts.filter { it.name.lowercase() !in usedEmails }
                if (free.size == 1) {
                    email = free[0].name
                    Log.i(TAG, "Drive ${storage.id}: email from sole free Google account")
                } else {
                    for (account in free) {
                        val fresh = runCatching {
                            Identity.getAuthorizationClient(context)
                                .authorize(GoogleDriveGalleryAuth.authorizationRequest(account))
                                .await()
                        }.getOrNull() ?: continue
                        if (fresh.hasResolution()) continue
                        val access = fresh.accessToken?.takeIf { it.isNotBlank() } ?: continue
                        val fromApi = GoogleDriveStorageProvider.fetchEmail(access)
                        val resolved = fromApi
                            ?: fresh.toGoogleSignInAccount()?.email
                            ?: account.name
                        if (resolved.lowercase() in usedEmails) continue
                        token = access
                        email = resolved
                        break
                    }
                }
            }

            // Несколько Drive без login и несколько аккаунтов — сопоставить по порядку
            if (email.isNullOrBlank() && missing.size == googleAccounts.size) {
                val idx = missing.indexOf(storage)
                if (idx in googleAccounts.indices) {
                    val candidate = googleAccounts[idx].name
                    if (candidate.lowercase() !in usedEmails) {
                        email = candidate
                        Log.i(TAG, "Drive ${storage.id}: email by account index $idx")
                    }
                }
            }

            if (email.isNullOrBlank()) {
                Log.w(
                    TAG,
                    "Could not backfill email for Drive ${storage.id}; " +
                            "googleAccounts=${googleAccounts.map { it.name }}, used=$usedEmails"
                )
                continue
            }
            usedEmails.add(email.lowercase())
            storageDao.upsert(
                storage.copy(
                    login = email,
                    password = token ?: storage.password,
                )
            )
            storageProviderRegistry.invalidate(storage.id)
            storageAuthProvider.refresh()
            Log.i(TAG, "Backfilled Google Drive email for storage ${storage.id}: $email")
        }
    }

    fun cancelGoogleDriveReauth(storageId: Long) {
        clearReauthInFlight(storageId)
    }

    /**
     * Access token Drive живёт ~1 час. Тихо запрашиваем новый через Identity
     * **для email этого Storage** — без setAccount() Google отдаёт чужой аккаунт
     * и as010201 начинает открываться токеном asinosoft (404/403).
     * @return null — ждём consent UI, скан/загрузку пропускаем.
     */
    private suspend fun refreshGoogleDriveAccessToken(storage: Storage): Storage? {
        if (storage.type != StorageType.GOOGLE_DRIVE) return storage
        return withContext(Dispatchers.IO) {
            try {
                val expectedEmail = storage.login?.takeIf { it.isNotBlank() }
                    ?: storage.password?.let { GoogleDriveStorageProvider.fetchEmail(it) }
                if (expectedEmail.isNullOrBlank()) {
                    Log.w(
                        TAG,
                        "Drive ${storage.id}: no account email for refresh — keeping stored token",
                    )
                    return@withContext storage.takeIf { !it.password.isNullOrBlank() }
                }
                val account = Account(expectedEmail, "com.google")
                val result = Identity.getAuthorizationClient(context)
                    .authorize(GoogleDriveGalleryAuth.authorizationRequest(account))
                    .await()
                if (result.hasResolution()) {
                    val pending = result.pendingIntent
                    if (pending != null) {
                        offerDriveReauth(storage.id, pending)
                    } else {
                        markStorageAttention(
                            storage.id,
                            context.getString(
                                R.string.storage_reconnect_required,
                                storage.categoryTitle(context),
                            ),
                            reauth = true,
                        )
                    }
                    return@withContext null
                }
                val token = result.accessToken?.takeIf { it.isNotBlank() }
                    ?: return@withContext storage
                val tokenEmail = GoogleDriveStorageProvider.fetchEmail(token)
                    ?: result.toGoogleSignInAccount()?.email
                if (tokenEmail != null &&
                    !tokenEmail.equals(expectedEmail, ignoreCase = true)
                ) {
                    Log.w(
                        TAG,
                        "Drive ${storage.id}: got token for $tokenEmail, " +
                                "expected $expectedEmail — not saving",
                    )
                    return@withContext storage.takeIf { !it.password.isNullOrBlank() }
                }
                if (token == storage.password &&
                    expectedEmail.equals(storage.login, ignoreCase = true)
                ) {
                    return@withContext storage
                }
                val updated = storage.copy(
                    password = token,
                    login = expectedEmail,
                )
                storageDao.upsert(updated)
                storageProviderRegistry.invalidate(storage.id)
                Log.i(
                    TAG,
                    "Refreshed Google Drive access token for storage ${storage.id} ($expectedEmail)",
                )
                updated
            } catch (ex: Throwable) {
                Log.w(TAG, "Silent Drive token refresh failed for ${storage.id}", ex)
                // Не рвём открытие файла: пробуем токен из БД; UI-reauth — если нет токена.
                if (!storage.password.isNullOrBlank()) {
                    return@withContext storage
                }
                markStorageAttention(
                    storage.id,
                    context.getString(
                        R.string.storage_reconnect_required,
                        storage.categoryTitle(context),
                    ),
                    reauth = true,
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

    private fun isNetworkAvailable(): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return true
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    private fun isAuthExpiredFailure(ex: Throwable): Boolean {
        val text = buildString {
            append(ex.message.orEmpty())
            append(' ')
            append(ex.cause?.message.orEmpty())
        }.lowercase()
        return "401" in text ||
                "expired_access_token" in text ||
                "invalid_access_token" in text ||
                "authorization failed" in text ||
                "unauthorized" in text
    }

    companion object {
        const val NETWORK_LINK_MARKER = "{{NETWORK}}"
        const val NETWORK_LINK_START = "{{LINK}}"
        const val NETWORK_LINK_END = "{{/LINK}}"
        private const val TAG = "data"
    }
}
