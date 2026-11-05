package com.asinosoft.photogallery.data.storage

import android.net.Uri
import com.asinosoft.photogallery.data.Media
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import okhttp3.Request

interface StorageProvider {

    val storage: Storage

    /** Отметка «Избранное» хранится в самом хранилище и приходит при сканировании */
    val syncsFavorites: Boolean get() = false

    /** Провайдер умеет инкремент (delta / cursor / sync-collection). */
    val supportsIncrementalSync: Boolean get() = false

    fun authorize(request: Request): Request = request

    suspend fun checkConnection(): StorageCheckResult

    /** Полное число файлов, если хранилище может сообщить его до сканирования. */
    fun getMediaCount(): Int? = null

    suspend fun fetchAll(): Flow<Media>

    /**
     * Инкрементальные изменения с [cursor].
     * При протухшем курсоре — [SyncCursorExpiredException].
     */
    suspend fun fetchChanges(cursor: String): Flow<StorageChange> = emptyFlow()

    /**
     * Курсор после [fetchAll] / [fetchChanges] для сохранения в [Storage.syncCursor].
     * `null` — не менять / не поддерживается.
     */
    fun takeSyncCursor(): String? = null

    suspend fun fetchOne(uri: Uri): Media?

    suspend fun getMediaUri(media: Media): Uri

    suspend fun getThumbnailUri(media: Media): Uri
}
