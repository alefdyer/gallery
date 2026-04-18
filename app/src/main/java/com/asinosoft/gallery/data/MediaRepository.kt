package com.asinosoft.gallery.data

import android.net.Uri
import kotlinx.coroutines.flow.Flow

interface MediaRepository {
    suspend fun fetchAll(): Flow<Media>

    suspend fun fetchOne(uri: Uri): Media
}
