package com.asinosoft.gallery.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumCategory
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.AlbumService
import com.asinosoft.gallery.data.CategoryWithAlbums
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.MediaDao
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageDao
import com.asinosoft.gallery.data.storage.StorageStatistics
import com.asinosoft.gallery.data.storage.StorageType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class StorageInfo(
    val storage: Storage,
    val statistics: StorageStatistics,
    val cover: Media?,
)

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    private val albumService: AlbumService,
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
    storageDao: StorageDao
) : ViewModel() {
    val albumsFlow = MutableStateFlow<List<CategoryWithAlbums>>(listOf())
    val albums: StateFlow<List<CategoryWithAlbums>> = albumsFlow

    val storages: StateFlow<List<StorageInfo>> = storageDao.getStorages().map { storages ->
            storages.filterNot { it.type == StorageType.LOCAL }.map { storage ->
                    StorageInfo(
                        storage,
                        mediaDao.getStorageStatistics(storage.id),
                        mediaDao.getStorageCover(storage.id)
                    )
                }
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    init {
        viewModelScope.launch {
            albumDao.getAlbums().collect { albums ->
                val categories = albums.groupBy { it.category }.map {
                    CategoryWithAlbums(
                        it.key, it.value
                    )
                }.sortedBy { it.category.name }

                albumsFlow.emit(categories)
            }
        }
    }

    fun moveAlbumIntoCategory(album: Album, category: AlbumCategory) = viewModelScope.launch {
        albumService.moveAlbumIntoCategory(album, category)
    }

    fun moveAlbumIntoNewCategory(album: Album, categoryName: String) = viewModelScope.launch {
        albumService.moveAlbumIntoNewCategory(album, categoryName)
    }
}
