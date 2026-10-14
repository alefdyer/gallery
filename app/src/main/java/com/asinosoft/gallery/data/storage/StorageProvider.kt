package com.asinosoft.gallery.data.storage

import android.net.Uri
import com.asinosoft.gallery.data.Media
import kotlinx.coroutines.flow.Flow
import okhttp3.Request

interface StorageProvider {

    val storage: Storage

    /** Отметка «Избранное» хранится в самом хранилище и приходит при сканировании */
    val syncsFavorites: Boolean get() = false

    fun authorize(request: Request): Request = request

    suspend fun checkConnection(): StorageCheckResult

    suspend fun fetchAll(): Flow<Media>

    suspend fun fetchOne(uri: Uri): Media?

    suspend fun getMediaUri(media: Media): Uri

    suspend fun getThumbnailUri(media: Media): Uri
}
