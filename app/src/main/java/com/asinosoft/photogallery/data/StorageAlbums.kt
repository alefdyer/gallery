package com.asinosoft.photogallery.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.asinosoft.photogallery.GalleryApp
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.data.storage.StorageDao
import com.asinosoft.photogallery.data.storage.StorageType
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * У каждого удалённого хранилища — своя категория альбомов-папок.
 * В базе ключ [AlbumCategory.storageCategoryName]; на вкладке — иконка и имя хранилища.
 */
@Singleton
class StorageAlbums @Inject constructor(
    private val albumDao: AlbumDao,
    private val storageDao: StorageDao,
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    suspend fun albumFor(name: String, storageId: Long): Album =
        albumDao.getOrCreateAlbum(name, categoryId(storageId))

    suspend fun ensureCategory(storage: Storage): Long {
        if (storage.type == StorageType.LOCAL) return AlbumCategory.OTHER.id
        return categoryId(storage.id)
    }

    suspend fun ensureAllRemoteCategories() {
        storageDao.getStorages().first()
            .filter { it.type != StorageType.LOCAL }
            .forEach { ensureCategory(it) }
        dropLegacyWebDavCategoryIfEmpty()
    }

    suspend fun migrateOnce() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        runCatching { migrateLegacyWebDavCategory() }
            .onFailure { Log.w(GalleryApp.TAG, "Storage albums webdav migrate failed", it) }
        runCatching { migrateRemoteFromOther() }
            .onFailure { Log.w(GalleryApp.TAG, "Storage albums other migrate failed", it) }
        dropLegacyWebDavCategoryIfEmpty()

        prefs.edit { putBoolean(KEY_MIGRATED, true) }
    }

    /** Устаревшая общая категория :webdav — больше не используется. */
    suspend fun dropLegacyWebDavCategoryIfEmpty() {
        val legacy = albumDao.findCategoryByName(AlbumCategory.WEBDAV_NAME) ?: return
        val hasAlbums = albumDao.getAlbums().first()
            .any { it.album.categoryId == legacy.id }
        if (!hasAlbums) {
            albumDao.deleteCategory(legacy.id)
        }
    }

    private suspend fun migrateLegacyWebDavCategory() {
        val legacy = albumDao.findCategoryByName(AlbumCategory.WEBDAV_NAME) ?: return
        val albums = albumDao.getAlbums().first()
            .filter { it.album.categoryId == legacy.id }
        for (withCover in albums) {
            val album = withCover.album
            val byStorage = albumDao.getStorageIdsInAlbum(album.id)
            if (byStorage.isEmpty()) {
                albumDao.delete(album.id)
                continue
            }
            for (storageId in byStorage) {
                val target = albumDao.getOrCreateAlbum(album.name, categoryId(storageId))
                if (target.id == album.id) continue
                val mediaIds = albumDao.getMediaIdsInAlbumOfStorage(album.id, storageId)
                if (mediaIds.isEmpty()) continue
                albumDao.addMediaToAlbum(mediaIds, target.id)
                albumDao.removeMediaFromAlbum(mediaIds, album.id)
                albumDao.updateAlbumStats(target.id)
            }
            albumDao.updateAlbumStats(album.id)
        }
        dropLegacyWebDavCategoryIfEmpty()
        albumDao.deleteEmptyCategories()
    }

    private suspend fun migrateRemoteFromOther() {
        val albumIds = albumDao.getAlbumMediaSources(AlbumCategory.OTHER.id)
            .filter { it.storageType != StorageType.LOCAL }
            .map { it.albumId }
            .distinct()
        for (albumId in albumIds) {
            runCatching {
                val album = albumDao.getAlbumById(albumId)
                val byStorage = albumDao.getStorageIdsInAlbum(albumId).filter { sid ->
                    runCatching { storageDao.getStorageById(sid).type != StorageType.LOCAL }
                        .getOrDefault(false)
                }
                if (byStorage.isEmpty()) return@runCatching
                for (storageId in byStorage) {
                    val target = albumDao.getOrCreateAlbum(album.name, categoryId(storageId))
                    if (target.id == albumId) continue
                    val mediaIds = albumDao.getMediaIdsInAlbumOfStorage(albumId, storageId)
                    if (mediaIds.isEmpty()) continue
                    albumDao.addMediaToAlbum(mediaIds, target.id)
                    albumDao.removeMediaFromAlbum(mediaIds, albumId)
                    albumDao.updateAlbumStats(target.id)
                }
                albumDao.updateAlbumStats(albumId)
            }.onFailure {
                Log.w(GalleryApp.TAG, "Can't move album $albumId to storage category", it)
            }
        }
    }

    private suspend fun categoryId(storageId: Long): Long = mutex.withLock {
        val key = "$KEY_CATEGORY_PREFIX$storageId"
        val savedId = prefs.getLong(key, 0L)
        if (savedId > 0) {
            val saved = albumDao.getCategoryById(savedId)
            if (null != saved) return@withLock savedId
        }

        val name = AlbumCategory.storageCategoryName(storageId)
        val existing = albumDao.findCategoryByName(name)
        val id = existing?.id ?: albumDao.createCategory(AlbumCategory(name = name))
        prefs.edit { putLong(key, id) }
        id
    }

    private companion object {
        const val PREFS_NAME = "storage_albums"
        const val KEY_CATEGORY_PREFIX = "category_"
        const val KEY_MIGRATED = "migrated_v1"
    }
}
