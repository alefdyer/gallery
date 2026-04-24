package com.asinosoft.gallery.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.MediaDao
import com.asinosoft.gallery.data.MediaService
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageAuthProvider
import com.asinosoft.gallery.data.storage.StorageDao
import com.asinosoft.gallery.data.storage.StorageProviderRegistry
import com.asinosoft.gallery.data.storage.StorageType
import com.asinosoft.gallery.data.storage.local.LocalStorageObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val service: MediaService,
    private val albumDao: AlbumDao,
    private val storageDao: StorageDao,
    @param:ApplicationContext private val context: Context,
    private val storageProviderRegistry: StorageProviderRegistry,
    private val storageAuthProvider: StorageAuthProvider,
    mediaDao: MediaDao
) : ViewModel() {
    private val albumId = MutableStateFlow<Long?>(null)
    private val rescanFlow = MutableStateFlow(false)
    private val messageFlow = MutableStateFlow<String?>(null)

    val albums = albumDao.getAlbums()

    val images = mediaDao.getImages()

    val storages = storageDao.getAccounts()

    val isRescanning: StateFlow<Boolean> = rescanFlow

    val message: StateFlow<String?> = messageFlow

    @OptIn(ExperimentalCoroutinesApi::class)
    val albumImages = albumId.filterNotNull().flatMapLatest { albumDao.getMediaInAlbum(it) }

    suspend fun clearMessage() {
        messageFlow.emit(null)
    }

    fun start() = viewModelScope.launch {
        storageDao.getStorages().forEach { storage ->
            if (storage.type == StorageType.LOCAL) {
                LocalStorageObserver.schedule(context, storage)
            }
        }

        rescan()
    }

    fun rescan() = viewModelScope.launch {
        rescanFlow.emit(true)
        try {
            storages.first().forEach { storage ->
                val provider = storageProviderRegistry.getStorageProvider(storage)
                service.update(provider)
            }
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
        rescanFlow.emit(false)
    }

    fun setAlbumId(id: Long) = viewModelScope.launch {
        albumId.emit(id)
    }

    fun delete(mediaIds: Collection<Long>, context: Context, callback: () -> Unit) =
        viewModelScope.launch {
            try {
                service.delete(mediaIds, context, callback)
            } catch (ex: Throwable) {
                messageFlow.emit(ex.message)
            }
        }

    fun edit(mediaId: Long, context: Context) = viewModelScope.launch {
        try {
            service.edit(mediaId, context)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun share(mediaIds: Collection<Long>, context: Context) = viewModelScope.launch {
        try {
            service.share(mediaIds, context)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun addToAlbum(mediaIds: Collection<Long>, albumId: Long) = viewModelScope.launch {
        try {
            service.addToAlbum(mediaIds, albumId)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun addToNewAlbum(mediaIds: Collection<Long>, name: String) = viewModelScope.launch {
        try {
            val album = service.createAlbum(name)
            service.addToAlbum(mediaIds, album.id)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun removeFromAlbum(mediaIds: Collection<Long>, albumId: Long) = viewModelScope.launch {
        try {
            service.removeFromAlbum(mediaIds, albumId)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun addStorage(storage: Storage) = viewModelScope.launch {
        try {
            storageDao.upsert(storage)
            storageAuthProvider.refresh()
            val provider = storageProviderRegistry.getStorageProvider(storage)
            service.update(provider)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun deleteStorage(storage: Storage) = viewModelScope.launch {
        try {
            storageDao.delete(storage)
            storageAuthProvider.refresh()
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }
}
