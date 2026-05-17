package com.asinosoft.gallery.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.MessageBus
import com.asinosoft.gallery.data.launchAndCatch
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageDao
import com.asinosoft.gallery.data.storage.StorageService
import com.asinosoft.gallery.data.storage.StorageType
import com.asinosoft.gallery.data.storage.local.LocalStorageObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.collections.forEach
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

@HiltViewModel
class MainViewModel @Inject constructor(
    storageDao: StorageDao,
    private val storageService: StorageService,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    private val storages: StateFlow<List<Storage>> = storageDao.getAccounts().stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = emptyList()
    )

    val isFetching = storageService.isFetching
    val messages = MessageBus.messages

    fun start() = viewModelScope.launchAndCatch {
        storages.first().forEach { storage ->
            if (storage.type == StorageType.LOCAL) {
                LocalStorageObserver.schedule(context, storage)

                storageService.fetch(storage)
            }
        }
    }

    fun fetch() {
        val storages = runBlocking { storages.first() }

        storages.forEach {
            viewModelScope.launchAndCatch {
                storageService.fetch(it)
            }
        }
    }
}
