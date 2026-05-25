package com.asinosoft.gallery.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.MessageBus
import com.asinosoft.gallery.data.launchAndCatch
import com.asinosoft.gallery.data.storage.StorageDao
import com.asinosoft.gallery.data.storage.StorageService
import com.asinosoft.gallery.data.storage.StorageType
import com.asinosoft.gallery.data.storage.local.LocalStorageObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.first

@HiltViewModel
class MainViewModel @Inject constructor(
    private val storageDao: StorageDao,
    private val storageService: StorageService,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    val isFetching = storageService.isFetching
    val messages = MessageBus.messages

    fun start() = viewModelScope.launchAndCatch {
        storageDao.getAccounts().first().forEach { storage ->
            if (storage.type == StorageType.LOCAL) {
                LocalStorageObserver.schedule(context, storage)

                storageService.fetch(storage)
            }
        }
    }

    fun fetch() = viewModelScope.launchAndCatch {
        storageDao.getAccounts().first().forEach {
            storageService.fetch(it)
        }
    }
}
