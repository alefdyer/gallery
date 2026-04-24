package com.asinosoft.gallery.data.storage

import android.net.Uri
import com.asinosoft.gallery.data.Media
import kotlinx.coroutines.flow.Flow
import okhttp3.Request

interface StorageProvider {
    val storage: Storage

    fun authorize(request: Request): Request = request

    suspend fun fetchAll(): Flow<Media>

    suspend fun fetchOne(uri: Uri): Media?
}
