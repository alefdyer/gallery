package com.asinosoft.photogallery.model

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.asinosoft.photogallery.GalleryApp
import com.asinosoft.photogallery.data.Album
import com.asinosoft.photogallery.data.AlbumCategory
import com.asinosoft.photogallery.data.AppAnalytics
import com.asinosoft.photogallery.data.AlbumDao
import com.asinosoft.photogallery.data.AlbumService
import com.asinosoft.photogallery.data.AlbumWithCover
import com.asinosoft.photogallery.data.CategoryWithAlbums
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.MediaDao
import com.asinosoft.photogallery.data.StorageAlbums
import com.asinosoft.photogallery.data.isStorageCategory
import com.asinosoft.photogallery.data.storageIdOrNull
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.data.storage.StorageDao
import com.asinosoft.photogallery.data.storage.MediaStatistics
import com.asinosoft.photogallery.data.storage.StorageService
import com.asinosoft.photogallery.data.storage.StorageType
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
    val statistics: MediaStatistics,
    val cover: Media?,
)

/** Метаданные удалённого хранилища для заголовка категории на вкладке «Альбомы». */
data class StorageCategoryInfo(
    val storage: Storage,
    val title: String,
)

@HiltViewModel
class AlbumsViewModel @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val albumService: AlbumService,
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
    private val analytics: AppAnalytics,
    private val storageAlbums: StorageAlbums,
    private val storageService: StorageService,
    private val storageDao: StorageDao
) : ViewModel() {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val categoryOrder = MutableStateFlow(loadCategoryOrder())
    private val alwaysExpandedCategoriesFlow = MutableStateFlow(loadExpandedCategories())
    val alwaysExpandedCategories: StateFlow<Set<Long>> = alwaysExpandedCategoriesFlow
    private val expandedCategoriesFlow = MutableStateFlow(alwaysExpandedCategoriesFlow.value)
    val expandedCategories: StateFlow<Set<Long>> = expandedCategoriesFlow
    private val storageTitleOverrides = MutableStateFlow(loadStorageTitleOverrides())

    val fetchingStorageIds: StateFlow<Set<Long>> = storageService.fetchingStorageIds

    val remoteStorages: StateFlow<Map<Long, StorageCategoryInfo>> =
        combine(storageDao.getStorages(), storageTitleOverrides) { storages, overrides ->
            storages
                .filter { it.type != StorageType.LOCAL }
                .associate { storage ->
                    val title = overrides[storage.id]?.takeIf { it.isNotBlank() }
                        ?: storage.categoryTitle(context)
                    storage.id to StorageCategoryInfo(storage, title)
                }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    init {
        viewModelScope.launch {
            runCatching { storageAlbums.migrateOnce() }
                .onFailure { Log.w(GalleryApp.TAG, "Storage album migration failed", it) }
        }
        viewModelScope.launch {
            storageDao.getStorages().collect { storages ->
                runCatching { storageAlbums.ensureAllRemoteCategories() }
                    .onFailure {
                        Log.w(GalleryApp.TAG, "Can't ensure storage categories", it)
                    }
            }
        }
    }

    val albums: StateFlow<List<CategoryWithAlbums>> =
        combine(
            albumDao.getAlbums(),
            albumDao.getAlbumCategories(),
            categoryOrder,
            remoteStorages
        ) { albums, categories, order, remotes ->
            val byCategoryId = albums.groupBy { it.album.categoryId }
            val remoteCategoryNames = remotes.keys
                .map { AlbumCategory.storageCategoryName(it) }
                .toSet()
            categories
                .map { category ->
                    val categoryAlbums = byCategoryId[category.id].orEmpty().sortedWith(
                        compareByDescending<AlbumWithCover> { it.album.date }
                            .thenByDescending { it.cover?.time }
                            .thenBy { it.album.name }
                    )
                    CategoryWithAlbums(category, categoryAlbums)
                }
                .filter {
                    // Пустые — только живые категории подключённых хранилищ (спиннер скана).
                    it.albums.isNotEmpty() || it.category.name in remoteCategoryNames
                }
                // Скрыть категории удалённых хранилищ и устаревший :webdav.
                .filter {
                    if (it.category.name == AlbumCategory.WEBDAV_NAME) return@filter false
                    val sid = it.category.storageIdOrNull() ?: return@filter true
                    sid in remotes
                }
                .sortedWith(
                    compareBy<CategoryWithAlbums> { category ->
                        order.indexOf(category.category.id).takeIf { it >= 0 } ?: Int.MAX_VALUE
                    }.thenBy { it.category.name }
                )
        }.stateIn(
            scope = viewModelScope, started = SharingStarted.Eagerly, initialValue = emptyList()
        )

    /** Карточки хранилищ больше не используются — облака как категории с папками. */
    val storages: StateFlow<List<StorageInfo>> =
        MutableStateFlow(emptyList())

    fun moveAlbumIntoCategory(album: Album, category: AlbumCategory) = viewModelScope.launch {
        albumService.moveAlbumIntoCategory(album, category)
    }

    fun moveAlbumIntoNewCategory(album: Album, categoryName: String) = viewModelScope.launch {
        albumService.moveAlbumIntoNewCategory(album, categoryName)
    }

    fun renameCategory(category: AlbumCategory, name: String) = viewModelScope.launch {
        val newName = name.trim()
        if (newName.isEmpty()) return@launch

        val storageId = category.storageIdOrNull()
        if (storageId != null) {
            renameStorageCategoryTitle(storageId, newName)
            return@launch
        }
        if (category.name.startsWith(":")) return@launch
        if (newName == category.name) return@launch

        runCatching { albumDao.renameCategory(category.id, newName) }
            .onSuccess { analytics.categoryRenamed() }
            .onFailure { Log.w(GalleryApp.TAG, "Can't rename category ${category.id}", it) }
    }

    /** Имя категории хранилища по умолчанию (без пользовательского override). */
    fun defaultStorageCategoryTitle(storageId: Long): String? {
        val storage = remoteStorages.value[storageId]?.storage
            ?: return null
        return storage.categoryTitle(context)
    }

    private fun renameStorageCategoryTitle(storageId: Long, title: String) {
        val defaultTitle = defaultStorageCategoryTitle(storageId) ?: return
        val trimmed = title.trim()
        if (trimmed.isEmpty()) return
        prefs.edit {
            if (trimmed == defaultTitle) {
                remove(storageTitleKey(storageId))
            } else {
                putString(storageTitleKey(storageId), trimmed)
            }
        }
        storageTitleOverrides.value = loadStorageTitleOverrides()
        analytics.categoryRenamed()
    }

    fun renameAlbum(album: Album, name: String) = viewModelScope.launch {
        val category = albumDao.getCategoryById(album.categoryId)
        val allowed = album.userCreated || (null != category && !category.name.startsWith(":"))
        if (!allowed) return@launch

        val newName = name.trim()
        if (newName.isEmpty() || newName == album.name) return@launch

        runCatching { albumDao.renameAlbum(album.id, newName) }
            .onSuccess { analytics.albumRenamed() }
            .onFailure { Log.w(GalleryApp.TAG, "Can't rename album ${album.id}", it) }
    }

    fun setCategoryOrder(ids: List<Long>) {
        categoryOrder.value = ids
        prefs.edit { putString(KEY_ORDER, ids.joinToString(",")) }
        analytics.categoryMoved()
    }

    fun toggleCategoryExpanded(id: Long) {
        val current = expandedCategoriesFlow.value
        expandedCategoriesFlow.value = if (id in current) current - id else current + id
    }

    fun setCategoryAlwaysExpanded(id: Long, expanded: Boolean) {
        val updated = alwaysExpandedCategoriesFlow.value.let { if (expanded) it + id else it - id }
        alwaysExpandedCategoriesFlow.value = updated
        prefs.edit { putString(KEY_EXPANDED, updated.joinToString(",")) }

        val current = expandedCategoriesFlow.value
        expandedCategoriesFlow.value = if (expanded) current + id else current - id
    }

    fun refreshStorage(storage: Storage) {
        storageService.enqueueFetch(storage)
    }

    fun refreshCategoryStorage(category: AlbumCategory) {
        val storageId = category.storageIdOrNull() ?: run {
            Log.w(GalleryApp.TAG, "Refresh: not a storage category ${category.name}")
            return
        }
        viewModelScope.launch {
            val storage = runCatching { storageDao.getStorageById(storageId) }.getOrNull()
            if (storage == null) {
                Log.w(GalleryApp.TAG, "Refresh: storage $storageId not found")
                return@launch
            }
            Log.i(GalleryApp.TAG, "Refresh storage category ${category.name} → $storage")
            storageService.enqueueFetch(storage)
        }
    }

    private fun loadExpandedCategories(): Set<Long> =
        prefs.getString(KEY_EXPANDED, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            ?: emptySet()

    private fun loadCategoryOrder(): List<Long> =
        prefs.getString(KEY_ORDER, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?: emptyList()

    private fun loadStorageTitleOverrides(): Map<Long, String> =
        prefs.all.mapNotNull { (key, value) ->
            if (!key.startsWith(KEY_TITLE_PREFIX)) return@mapNotNull null
            val id = key.removePrefix(KEY_TITLE_PREFIX).toLongOrNull() ?: return@mapNotNull null
            val title = (value as? String)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            id to title
        }.toMap()

    private companion object {
        const val PREFS_NAME = "album_categories"
        const val KEY_ORDER = "order"
        const val KEY_EXPANDED = "expanded"
        const val KEY_TITLE_PREFIX = "title_"

        fun storageTitleKey(storageId: Long) = "$KEY_TITLE_PREFIX$storageId"
    }
}
