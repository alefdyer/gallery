package com.asinosoft.gallery.data.storage

import android.net.Uri
import com.asinosoft.gallery.data.Media
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

abstract class NoopRemoteStorageProvider : StorageProvider {
    final override suspend fun checkConnection() =
        StorageCheckResult.UnknownError("Not implemented")

    final override suspend fun fetchAll(): Flow<Media> = emptyFlow()

    final override suspend fun fetchOne(uri: Uri): Media? = null
}
