package com.asinosoft.gallery.model

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.gallery.GalleryApp
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumCategory
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.AlbumService
import com.asinosoft.gallery.data.AlbumWithCover
import com.asinosoft.gallery.data.CategoryWithAlbums
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.MediaDao
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageDao
import com.asinosoft.gallery.data.storage.StorageStatistics
import com.asinosoft.gallery.data.storage.StorageType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
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
    @param:ApplicationContext private val context: Context,
    private val albumService: AlbumService,
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
    storageDao: StorageDao
) : ViewModel() {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val categoryOrder = MutableStateFlow(loadCategoryOrder())

    val albums: StateFlow<List<CategoryWithAlbums>> =
        combine(albumDao.getAlbums(), categoryOrder) { albums, order ->
            albums
                .groupBy { it.category }
                .map { (category, albums) ->
                    CategoryWithAlbums(
                        category,
                        albums.sortedWith(
                            compareByDescending<AlbumWithCover> { it.album.date }
                                .thenByDescending { it.cover?.time }
                                .thenBy { it.album.name }
                        )
                    )
                }
                .sortedWith(
                    compareBy<CategoryWithAlbums> { category ->
                        order.indexOf(category.category.id).takeIf { it >= 0 } ?: Int.MAX_VALUE
                    }.thenBy { it.category.name }
                )
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    val storages: StateFlow<List<StorageInfo>> = combine(
        storageDao.getStorages(),
        mediaDao.observeMediaCount()
    ) { storages, _ -> storages }.map { storages ->
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

    fun moveAlbumIntoCategory(album: Album, category: AlbumCategory) = viewModelScope.launch {
        albumService.moveAlbumIntoCategory(album, category)
    }

    fun moveAlbumIntoNewCategory(album: Album, categoryName: String) = viewModelScope.launch {
        albumService.moveAlbumIntoNewCategory(album, categoryName)
    }

    fun renameCategory(category: AlbumCategory, name: String) = viewModelScope.launch {
        val newName = name.trim()
        if (newName.isEmpty() || newName == category.name) return@launch

        runCatching { albumDao.renameCategory(category.id, newName) }
            .onFailure { Log.w(GalleryApp.TAG, "Can't rename category ${category.id}", it) }
    }

    fun setCategoryOrder(ids: List<Long>) {
        categoryOrder.value = ids
        prefs.edit { putString(KEY_ORDER, ids.joinToString(",")) }
    }

    private fun loadCategoryOrder(): List<Long> =
        prefs.getString(KEY_ORDER, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()

    private companion object {
        const val PREFS_NAME = "album_categories"
        const val KEY_ORDER = "order"
    }
}
