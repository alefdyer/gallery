package com.asinosoft.photogallery.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.asinosoft.photogallery.GalleryApp
import com.asinosoft.photogallery.data.storage.StorageType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Альбомы из WebDAV попадают в отдельную категорию.
 * Категория создаётся при появлении первой такой папки; в базе хранится
 * системный ключ [AlbumCategory.WEBDAV_NAME], подпись — из строк локали.
 */
@Singleton
class WebDavAlbums @Inject constructor(
    private val albumDao: AlbumDao,
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    suspend fun albumFor(name: String): Album =
        albumDao.getOrCreateAlbum(name, categoryId())

    /** Создаёт категорию заранее, чтобы на вкладке Альбомы был заголовок со спиннером скана. */
    suspend fun ensureCategory(): Long = categoryId()

    suspend fun migrateOnce() {
        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        val albumIds = albumDao.getAlbumMediaSources(AlbumCategory.OTHER.id)
            .filter { it.storageType == StorageType.WEBDAV }
            .map { it.albumId }
            .distinct()

        if (albumIds.isNotEmpty()) {
            val categoryId = categoryId()
            albumIds.forEach { albumId ->
                runCatching {
                    val album = albumDao.getAlbumById(albumId)
                    val hasOtherTypes =
                        albumDao.countMediaInAlbumNotOfType(albumId, StorageType.WEBDAV) > 0
                    if (!hasOtherTypes) {
                        albumDao.upsert(album.copy(categoryId = categoryId))
                    } else {
                        val target = albumDao.getOrCreateAlbum(album.name, categoryId)
                        if (target.id != albumId) {
                            val mediaIds =
                                albumDao.getMediaIdsInAlbumOfType(albumId, StorageType.WEBDAV)
                            albumDao.addMediaToAlbum(mediaIds, target.id)
                            albumDao.removeMediaFromAlbum(mediaIds, albumId)
                            albumDao.updateAlbumStats(albumId)
                            albumDao.updateAlbumStats(target.id)
                        }
                    }
                }.onFailure {
                    Log.w(GalleryApp.TAG, "Can't move album $albumId to webdav", it)
                }
            }
        }

        prefs.edit { putBoolean(KEY_MIGRATED, true) }
    }

    private suspend fun categoryId(): Long = mutex.withLock {
        val savedId = prefs.getLong(KEY_CATEGORY_ID, 0L)
        if (savedId > 0) {
            val saved = albumDao.getCategoryById(savedId)
            if (null != saved) return@withLock savedId
        }

        val existing = albumDao.findCategoryByName(AlbumCategory.WEBDAV_NAME)
        val id = existing?.id
            ?: albumDao.createCategory(AlbumCategory(name = AlbumCategory.WEBDAV_NAME))
        prefs.edit { putLong(KEY_CATEGORY_ID, id) }
        id
    }

    private companion object {
        const val PREFS_NAME = "webdav_albums"
        const val KEY_CATEGORY_ID = "category_id"
        const val KEY_MIGRATED = "migrated"
    }
}
