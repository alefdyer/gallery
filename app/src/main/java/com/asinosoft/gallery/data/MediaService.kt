package com.asinosoft.gallery.data

import android.content.Context
import android.net.Uri

interface MediaService {
    suspend fun delete(mediaIds: Collection<Long>, context: Context, callback: () -> Unit)

    suspend fun edit(mediaId: Long, context: Context)

    suspend fun share(mediaIds: Collection<Long>, context: Context)

    suspend fun addToAlbum(mediaIds: Collection<Long>, albumId: Long)

    suspend fun removeFromAlbum(mediaIds: Collection<Long>, albumId: Long)

    suspend fun createAlbum(name: String): Album

    suspend fun update(uri: Uri)

    suspend fun updateAll()
}
