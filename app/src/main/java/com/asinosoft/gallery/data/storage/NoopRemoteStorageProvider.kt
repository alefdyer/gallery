package com.asinosoft.gallery.data.storage

import android.net.Uri
import com.asinosoft.gallery.data.Media
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

abstract class NoopRemoteStorageProvider(final override val type: StorageType) : StorageProvider {
    final override suspend fun fetchAll(): Flow<Media> = emptyFlow()

    final override suspend fun fetchOne(uri: Uri): Media? = null
}
