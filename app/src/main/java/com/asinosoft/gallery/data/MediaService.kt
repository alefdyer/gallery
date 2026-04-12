package com.asinosoft.gallery.data

import android.content.Context
import android.net.Uri
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest

interface MediaService {
    suspend fun delete(
        media: Collection<Media>,
        context: Context,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    )

    suspend fun postDelete(media: Collection<Media>)

    suspend fun edit(media: Media, context: Context)

    suspend fun share(media: Collection<Media>, context: Context)

    suspend fun addToAlbum(media: Collection<Media>, album: Album)

    suspend fun removeFromAlbum(media: Collection<Media>, album: Album)

    suspend fun createAlbum(name: String): Album

    suspend fun update(uri: Uri)

    suspend fun updateAll()
}
