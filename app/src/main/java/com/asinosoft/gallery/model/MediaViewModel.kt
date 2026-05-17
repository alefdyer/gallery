package com.asinosoft.gallery.model

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.storage.StorageProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class MediaViewModel @Inject constructor(
    private val storageProviderRegistry: StorageProviderRegistry
) : ViewModel() {
    suspend fun getMediaUri(media: Media): Uri =
        storageProviderRegistry.getStorageProvider(media.storageId)
            .getMediaUri(media)

    suspend fun getThumbnailUri(media: Media): Uri =
        storageProviderRegistry.getStorageProvider(media.storageId)
            .getThumbnailUri(media)
}
