package com.asinosoft.gallery.model

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
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
import kotlinx.coroutines.flow.first

@HiltViewModel
class MainViewModel @Inject constructor(
    private val storageDao: StorageDao,
    private val storageService: StorageService,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    val messages = MessageBus.messages
    private var contentObserver: ContentObserver? = null

    fun start() = viewModelScope.launchAndCatch {
        storageDao.getAccounts().first().forEach { storage ->
            if (storage.type == StorageType.LOCAL) {
                LocalStorageObserver.schedule(context, storage)

                registerContentObserver(storage)

                storageService.fetch(storage)
            }
        }
    }

    private fun registerContentObserver(storage: Storage) {
        if (contentObserver != null) return

        contentObserver = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean, uri: Uri?) {
                super.onChange(selfChange, uri)
                viewModelScope.launchAndCatch {
                    storageService.fetch(storage)
                }
            }
        }

        try {
            context.contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
            context.contentResolver.registerContentObserver(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                true,
                contentObserver!!
            )
        } catch (_: Throwable) {
        }
    }

    override fun onCleared() {
        super.onCleared()
        contentObserver?.let {
            try {
                context.contentResolver.unregisterContentObserver(it)
            } catch (_: Throwable) {
            }
        }
    }
}
