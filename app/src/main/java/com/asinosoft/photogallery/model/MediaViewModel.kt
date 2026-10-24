package com.asinosoft.photogallery.model

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.asinosoft.photogallery.data.EXTERNAL_STORAGE_ID
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.storage.StorageProviderRegistry
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
open class MediaViewModel @Inject constructor(
    private val storageProviderRegistry: StorageProviderRegistry
) : ViewModel() {
    suspend fun getMediaUri(media: Media): Uri =
        if (EXTERNAL_STORAGE_ID == media.storageId) {
            requireNotNull(media.uri)
        } else {
            storageProviderRegistry.getStorageProvider(media.storageId)
                .getMediaUri(media)
        }

    suspend fun getThumbnailUri(media: Media): Uri =
        storageProviderRegistry.getStorageProvider(media.storageId)
            .getThumbnailUri(media)
}
