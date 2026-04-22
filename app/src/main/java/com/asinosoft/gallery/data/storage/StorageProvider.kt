package com.asinosoft.gallery.data.storage

import android.net.Uri
import com.asinosoft.gallery.data.Media
import kotlinx.coroutines.flow.Flow

interface StorageProvider {
    val type: StorageType

    suspend fun fetchAll(): Flow<Media>

    suspend fun fetchOne(uri: Uri): Media?
}
