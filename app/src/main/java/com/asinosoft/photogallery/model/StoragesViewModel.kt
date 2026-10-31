package com.asinosoft.photogallery.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.photogallery.data.AppAnalytics
import com.asinosoft.photogallery.data.launchAndCatch
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.data.storage.StorageCheckResult
import com.asinosoft.photogallery.data.storage.StorageDao
import com.asinosoft.photogallery.data.storage.StorageService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

@HiltViewModel
class StoragesViewModel @Inject constructor(
    storageDao: StorageDao,
    private val storageService: StorageService,
    private val analytics: AppAnalytics
) : ViewModel() {
    val storages: StateFlow<List<Storage>> = storageDao.getAccounts().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    init {
        viewModelScope.launchAndCatch {
            storageService.backfillMissingGoogleDriveEmails()
        }
    }

    fun addStorage(storage: Storage) = viewModelScope.launchAndCatch {
        storageService.addStorage(storage)
        analytics.storageAdded(storage.type.name.lowercase())
    }

    suspend fun checkStorage(storage: Storage): StorageCheckResult =
        storageService.checkStorage(storage)

    fun deleteStorage(storage: Storage) = viewModelScope.launchAndCatch {
        storageService.deleteStorage(storage)
    }
}
