package com.asinosoft.gallery.data.backup

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumCategory
import com.asinosoft.gallery.data.AlbumDao
import com.asinosoft.gallery.data.AlbumService
import com.asinosoft.gallery.data.MediaBackupLookup
import com.asinosoft.gallery.data.MediaDao
import com.asinosoft.gallery.data.storage.StorageType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsBackupImporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
    private val albumService: AlbumService,
) {
    suspend fun import(
        backup: GallerySettingsBackup,
        mode: SettingsRestoreMode = SettingsRestoreMode.MERGE,
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): SettingsBackupResult =
        withContext(Dispatchers.IO) {
            val started = System.currentTimeMillis()
            var mediaLinked = 0
            var mediaNotFound = 0
            var favoritesRestored = 0
            var albumsRestored = 0

            // категории + индекс + альбомы + избранное
            val total = 2 + backup.albums.size + 1
            var current = 0
            onProgress(current, total)

            val categoryIdByName = mutableMapOf<String, Long>()
            albumDao.getAllCategories().forEach { categoryIdByName[it.name] = it.id }

            for (category in backup.categories) {
                val name = category.name.trim()
                if (name.isEmpty()) continue
                if (null == categoryIdByName[name]) {
                    val id = albumDao.createCategory(AlbumCategory(name = name))
                    categoryIdByName[name] = id
                }
            }
            current++
            onProgress(current, total)

            val index = MediaLookupIndex(mediaDao.getAllBackupLookupRows())
            Log.i(
                TAG,
                "Loaded ${index.size} media for lookup in ${System.currentTimeMillis() - started}ms"
            )
            current++
            onProgress(current, total)

            val systemCategoryIds = buildSet {
                add(AlbumCategory.OTHER.id)
                categoryIdByName[AlbumCategory.MESSENGERS_NAME]?.let { add(it) }
            }

            val restoredAlbumIds = mutableSetOf<Long>()
            for (albumBackup in backup.albums) {
                val categoryName = albumBackup.category.ifBlank { AlbumCategory.OTHER.name }
                val categoryId = categoryIdByName[categoryName]
                    ?: categoryIdByName[AlbumCategory.OTHER.name]
                    ?: AlbumCategory.OTHER.id
                val album = resolveAlbum(
                    name = albumBackup.name,
                    categoryId = categoryId,
                    userCreated = albumBackup.userCreated,
                    systemCategoryIds = systemCategoryIds,
                )
                albumsRestored++
                restoredAlbumIds += album.id
                if (mode == SettingsRestoreMode.REPLACE) {
                    // Состав альбома заменяется, а не дополняется.
                    albumDao.clearAlbumMedia(album.id)
                }

                val mediaIds = ArrayList<Long>(albumBackup.media.size)
                for (ref in albumBackup.media) {
                    val media = index.resolve(ref)
                    if (null != media) {
                        mediaIds += media.id
                        mediaLinked++
                    } else {
                        mediaNotFound++
                    }
                }
                mediaIds.chunked(CHUNK).forEach { chunk ->
                    albumDao.addMediaToAlbum(chunk, album.id)
                }
                if (mediaIds.isNotEmpty()) {
                    albumService.updateAlbumCover(album.id)
                    albumDao.updateAlbumStats(album.id)
                }
                current++
                onProgress(current, total)
            }

            if (mode == SettingsRestoreMode.REPLACE) {
                // Удаляем структуру, которой нет в копии: альбомы (только записи
                // и связи, сами файлы не трогаем), затем опустевшие пользовательские
                // категории. Системные категории (":...") не удаляются никогда.
                albumDao.getAllAlbums()
                    .filter { it.id !in restoredAlbumIds }
                    .forEach { albumService.deleteAlbum(it) }
                val backupCategoryNames = backup.categories
                    .map { it.name.trim() }
                    .toSet()
                albumDao.getAllCategories()
                    .filter { category ->
                        category.name !in backupCategoryNames &&
                                !category.name.startsWith(":")
                    }
                    .forEach { albumDao.deleteCategory(it.id) }
                mediaDao.clearAllFavorites()
            }

            val favoriteIds = ArrayList<Long>(backup.favorites.size)
            for (ref in backup.favorites) {
                val media = index.resolve(ref)
                if (null != media) {
                    favoriteIds += media.id
                    favoritesRestored++
                } else {
                    mediaNotFound++
                }
            }
            favoriteIds.chunked(CHUNK).forEach { chunk ->
                mediaDao.setFavorite(chunk, true)
            }

            val prefs = context.getSharedPreferences(CATEGORY_PREFS, Context.MODE_PRIVATE)
            val orderIds = backup.categoryOrder.mapNotNull { categoryIdByName[it] }
            val expandedIds = backup.alwaysExpandedCategories.mapNotNull { categoryIdByName[it] }
            prefs.edit {
                if (mode == SettingsRestoreMode.REPLACE) {
                    // Перезапись: порядок из копии применяется и пустым значением.
                    if (orderIds.isEmpty()) remove(KEY_ORDER)
                    else putString(KEY_ORDER, orderIds.joinToString(","))
                    if (expandedIds.isEmpty()) remove(KEY_EXPANDED)
                    else putString(KEY_EXPANDED, expandedIds.joinToString(","))
                } else {
                    if (orderIds.isNotEmpty()) putString(KEY_ORDER, orderIds.joinToString(","))
                    if (expandedIds.isNotEmpty()) putString(
                        KEY_EXPANDED,
                        expandedIds.joinToString(",")
                    )
                }
            }
            current++
            onProgress(current, total)

            Log.i(
                TAG,
                "Import done in ${System.currentTimeMillis() - started}ms: " +
                        "albums=$albumsRestored linked=$mediaLinked favorites=$favoritesRestored missing=$mediaNotFound"
            )

            SettingsBackupResult(
                albumsRestored = albumsRestored,
                mediaLinked = mediaLinked,
                favoritesRestored = favoritesRestored,
                mediaNotFound = mediaNotFound,
                privateManifestCount = backup.privatePaths.size,
            )
        }

    /**
     * Восстанавливает альбом в категорию из бэкапа.
     * Если одноимённый альбом уже есть в «Прочее»/«Мессенджеры» (после переноса пользователем),
     * переносим/сливаем его — не создаём вторую копию в системной категории.
     */
    private suspend fun resolveAlbum(
        name: String,
        categoryId: Long,
        userCreated: Boolean,
        systemCategoryIds: Set<Long>,
    ): Album {
        val all = albumDao.findAlbumsByName(name)
        val inTarget = all.firstOrNull { it.categoryId == categoryId }
        val systemDupes = all.filter {
            it.categoryId in systemCategoryIds && it.categoryId != categoryId
        }

        var album = when {
            null != inTarget -> {
                val target = inTarget
                systemDupes.forEach { dupe ->
                    if (dupe.id != target.id) {
                        albumDao.mergeAlbums(dupe.id, target.id)
                    }
                }
                albumService.updateAlbumCover(target.id)
                albumDao.getAlbumById(target.id)
            }

            systemDupes.isNotEmpty() -> {
                val primary = systemDupes.first()
                systemDupes.drop(1).forEach { dupe ->
                    albumDao.mergeAlbums(dupe.id, primary.id)
                }
                albumDao.setAlbumCategory(primary.id, categoryId)
                albumService.updateAlbumCover(primary.id)
                albumDao.getAlbumById(primary.id)
            }

            else -> albumDao.getOrCreateAlbum(name, categoryId)
        }

        if (album.userCreated != userCreated || album.categoryId != categoryId) {
            album = album.copy(categoryId = categoryId, userCreated = userCreated)
            albumDao.upsert(album)
        }
        return album
    }

    private class MediaLookupIndex(rows: List<MediaBackupLookup>) {
        private val byPathAndName = HashMap<String, MutableList<MediaBackupLookup>>()
        private val byNameAndSize = HashMap<String, MediaBackupLookup>()

        val size: Int = rows.size

        init {
            for (row in rows) {
                val name = row.filename.substringAfterLast('/')
                byPathAndName.getOrPut(key(row.path, name)) { mutableListOf() }.add(row)
                if (row.size >= 0) {
                    byNameAndSize.putIfAbsent(key(name, row.size.toString()), row)
                }
            }
        }

        fun resolve(ref: BackupMediaRef): MediaBackupLookup? {
            val type = runCatching { StorageType.valueOf(ref.storageType) }.getOrNull()
            val candidates = byPathAndName[key(ref.path, ref.name)].orEmpty()
            val filtered = candidates.filter { media ->
                (null == type || media.storageType == type) &&
                        (null == ref.owner || media.owner == ref.owner) &&
                        (null == ref.size || media.size == ref.size)
            }
            if (filtered.isNotEmpty()) return filtered.first()
            if (null != ref.size) {
                byNameAndSize[key(ref.name, ref.size.toString())]?.let { return it }
            }
            return candidates.firstOrNull()
        }

        private fun key(a: String, b: String) = "$a\u0000$b"
    }

    private companion object {
        const val TAG = "SettingsBackupImport"
        const val CATEGORY_PREFS = "album_categories"
        const val KEY_ORDER = "order"
        const val KEY_EXPANDED = "expanded"
        const val CHUNK = 400
    }
}
