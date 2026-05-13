package com.asinosoft.gallery.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageCheckResult
import com.asinosoft.gallery.data.storage.StorageDao
import com.asinosoft.gallery.data.storage.StorageService
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class StoragesViewModel @Inject constructor(
    storageDao: StorageDao,
    private val storageService: StorageService
) : ViewModel() {
    val storages = storageDao.getAccounts()

    fun addStorage(storage: Storage) = viewModelScope.launch {
        storageService.addStorage(storage)
    }

    suspend fun checkStorage(storage: Storage): StorageCheckResult =
        storageService.checkStorage(storage)

    fun deleteStorage(storage: Storage) = viewModelScope.launch {
        storageService.deleteStorage(storage)
    }
}
