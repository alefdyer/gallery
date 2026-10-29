package com.asinosoft.photogallery.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.photogallery.data.storage.StorageService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class DriveReauthViewModel @Inject constructor(
    private val storageService: StorageService,
) : ViewModel() {
    val requests = storageService.driveReauthRequests

    fun applyToken(storageId: Long, token: String) {
        viewModelScope.launch {
            storageService.applyGoogleDriveToken(storageId, token, retryFetch = true)
        }
    }

    fun cancel(storageId: Long) {
        storageService.cancelGoogleDriveReauth(storageId)
    }
}
