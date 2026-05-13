package com.asinosoft.gallery.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.MessageBus
import com.asinosoft.gallery.data.storage.StorageDao
import com.asinosoft.gallery.data.storage.StorageService
import com.asinosoft.gallery.data.storage.StorageType
import com.asinosoft.gallery.data.storage.local.LocalStorageObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.last
import kotlinx.coroutines.launch
import javax.inject.Inject
import kotlin.collections.forEach

@HiltViewModel
class MainViewModel @Inject constructor(
    storageDao: StorageDao,
    private val storageService: StorageService,
    @param:ApplicationContext private val context: Context,
): ViewModel() {
    private val storages = storageDao.getAccounts()
    val isFetching = storageService.isFetching
    val messages = MessageBus.messages

    fun start() = viewModelScope.launch {
        storages.last().forEach { storage ->
            if (storage.type == StorageType.LOCAL) {
                LocalStorageObserver.schedule(context, storage)

                storageService.fetch(storage)
            }
        }
    }

    fun fetch() = viewModelScope.launch {
            storages.last().forEach {
                try {
                    storageService.fetch(it)
                } catch (ex: Throwable) {
                    MessageBus.dispatch(ex.message ?: "Failed to fetch images")
                }
            }
        }

}