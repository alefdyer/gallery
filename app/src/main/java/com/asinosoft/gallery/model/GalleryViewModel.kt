package com.asinosoft.gallery.model

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.MediaDao
import com.asinosoft.gallery.data.MediaService
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageDao
import com.asinosoft.gallery.data.storage.StorageProviderRegistry
import com.asinosoft.gallery.data.storage.StorageService
import com.asinosoft.gallery.data.storage.StorageType
import com.asinosoft.gallery.data.storage.local.LocalStorageObserver
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@HiltViewModel
class GalleryViewModel @Inject constructor(
    private val mediaService: MediaService,
    private val storageService: StorageService,
    private val albumDao: AlbumDao,
    private val storageDao: StorageDao,
    @param:ApplicationContext private val context: Context,
    private val storageProviderRegistry: StorageProviderRegistry,
    mediaDao: MediaDao
) : ViewModel() {
    private val albumId = MutableStateFlow<Long?>(null)
    private val messageFlow = MutableStateFlow<String?>(null)

    val albums = albumDao.getAlbums()

    val images = mediaDao.getImages()

    val storages = storageDao.getAccounts()

    val isRescanning: StateFlow<Boolean> = storageService.isFetching

    val message: StateFlow<String?> = messageFlow

    @OptIn(ExperimentalCoroutinesApi::class)
    val albumImages = albumId.filterNotNull().flatMapLatest { albumDao.getMediaInAlbum(it) }

    suspend fun clearMessage() {
        messageFlow.emit(null)
    }

    fun start() = viewModelScope.launch {
        val storages = storageDao.getStorages()

        storages.forEach { storage ->
            if (storage.type == StorageType.LOCAL) {
                LocalStorageObserver.schedule(context, storage)

                storageService.fetch(storage)
            }
        }
    }

    fun rescan(storages: List<Storage>) = viewModelScope.launch {
        try {
            storages.forEach { storageService.fetch(it) }
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun setAlbumId(id: Long) = viewModelScope.launch {
        albumId.emit(id)
    }

    fun delete(mediaIds: Collection<Long>, context: Context, callback: () -> Unit) =
        viewModelScope.launch {
            try {
                mediaService.delete(mediaIds, context, callback)
            } catch (ex: Throwable) {
                messageFlow.emit(ex.message)
            }
        }

    fun share(mediaIds: Collection<Long>, context: Context) = viewModelScope.launch {
        try {
            mediaService.share(mediaIds, context)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun addToAlbum(mediaIds: Collection<Long>, albumId: Long) = viewModelScope.launch {
        try {
            mediaService.addToAlbum(mediaIds, albumId)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun addToNewAlbum(mediaIds: Collection<Long>, name: String) = viewModelScope.launch {
        try {
            val album = mediaService.createAlbum(name)
            mediaService.addToAlbum(mediaIds, album.id)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    fun removeFromAlbum(mediaIds: Collection<Long>, albumId: Long) = viewModelScope.launch {
        try {
            mediaService.removeFromAlbum(mediaIds, albumId)
        } catch (ex: Throwable) {
            messageFlow.emit(ex.message)
        }
    }

    suspend fun getMediaUri(media: Media): Uri =
        storageProviderRegistry.getStorageProvider(media.storageId)
            .getMediaUri(media)

    suspend fun getThumbnailUri(media: Media): Uri =
        storageProviderRegistry.getStorageProvider(media.storageId)
            .getThumbnailUri(media)
}
