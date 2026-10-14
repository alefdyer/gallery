package com.asinosoft.gallery.data

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.edit
import com.asinosoft.gallery.GalleryApp
import com.asinosoft.gallery.data.storage.StorageProvider
import com.asinosoft.gallery.di.IntentHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MediaService @Inject constructor(
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
    private val messengerAlbums: MessengerAlbums,
    @param:ApplicationContext private val context: Context
) {
    private val intentHelper = IntentHelper
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun add(media: Media) {
        var mediaId = mediaDao.upsert(media)
        if (mediaId <= 0) {
            mediaId = mediaDao.getMediaId(media.storageId, media.storageItemId) ?: return
        }
        val albumName = media.path.split('/').lastOrNull { it.isNotEmpty() }
        albumName?.let {
            val album = messengerAlbums.albumFor(albumName, messengerAlbums.isMessengerMedia(media))
            addToAlbum(listOf(mediaId), album.id)
        }

        CoroutineScope(Dispatchers.IO).launch {
            ThumbnailCache.preload(context, mediaId, media.uri)
        }
    }

    suspend fun delete(mediaIds: Collection<Long>, context: Context, callback: () -> Unit) {
        val uris = withContext(Dispatchers.IO) { mediaDao.getUris(mediaIds) }
        intentHelper.delete(uris, context) {
            val albums = albumDao.getMediaAlbumIds(mediaIds)
            mediaDao.deleteAll(mediaIds)
            albums.forEach {
                albumDao.updateAlbumStats(it) }
            callback()
        }
    }

    suspend fun edit(mediaId: Long, context: Context) {
        val uri = withContext(Dispatchers.IO) { mediaDao.getUri(mediaId) }
        val edit =
            Intent().apply {
                action = Intent.ACTION_EDIT
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(edit)
    }

    suspend fun share(mediaIds: Collection<Long>, context: Context) {
        val uris = withContext(Dispatchers.IO) { mediaDao.getUris(mediaIds) }
        val send =
            if (1 == uris.size) {
                Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent().apply {
                    action = Intent.ACTION_SEND_MULTIPLE
                    type = "image/jpeg"

                    putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        uris.toCollection(ArrayList())
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        val chooser = Intent.createChooser(send, null)
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    suspend fun addToAlbum(mediaIds: Collection<Long>, albumId: Long) {
        albumDao.addMediaToAlbum(mediaIds, albumId)
        albumDao.updateAlbumStats(albumId)
    }

    suspend fun addToNewAlbum(mediaIds: Collection<Long>, albumName: String, albumCategory: AlbumCategory) {
        val albumName = albumName.trim()
        require(albumName.isNotEmpty()) { "Album name must not be empty" }

        val albumId = albumDao.upsert(Album(name = albumName, categoryId = albumCategory.id))
        addToAlbum(mediaIds, albumId)
    }

    suspend fun addToNewAlbumInNewCategory(
        mediaIds: Collection<Long>,
        albumName: String,
        categoryName: String
    ) {
        val categoryName = categoryName.trim()
        require(categoryName.isNotEmpty()) { "Category name must not be empty" }

        val category = albumDao.findCategoryByName(categoryName)
            ?: AlbumCategory(
                id = albumDao.createCategory(AlbumCategory(name = categoryName)),
                name = categoryName
            )
        addToNewAlbum(mediaIds, albumName, category)
    }

    suspend fun removeFromAlbum(mediaIds: Collection<Long>, albumId: Long) {
        if (mediaIds.isEmpty()) {
            return
        }
        albumDao.removeMediaFromAlbum(mediaIds, albumId)
        albumDao.updateAlbumStats(albumId)
    }

    suspend fun updateAll(providers: Collection<StorageProvider>): Unit =
        withContext(Dispatchers.IO) {
            Log.d(GalleryApp.TAG, "rescan")
            measureTimeMillis {
                providers.forEach { update(it) }
            }.also {
                Log.i(GalleryApp.TAG, "DONE in $it ms")
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun update(provider: StorageProvider) = withContext(Dispatchers.IO) {
        Log.i("MediaService", "update: ${provider.storage}")
        val updated = mutableSetOf<Long>()
        messengerAlbums.migrateOnce()
        provider.fetchAll().chunked(500).collect { fetched ->
            val media = mediaDao.getMediaByStorageItemIds(
                provider.storage.id,
                fetched.map {
                    it.storageItemId
                }
            )
            updated += media.map { it.id }

            val current = media.map { it.storageItemId }.toSet()
            val toInsert = fetched.filterNot { current.contains(it.storageItemId) }

            if (toInsert.isNotEmpty()) {
                val rawMediaIds = mediaDao.upsertAll(toInsert)
                val validMediaIds = rawMediaIds.mapIndexedNotNull { index, id ->
                    if (id > 0) id
                    else {
                        val item = toInsert[index]
                        mediaDao.getMediaId(item.storageId, item.storageItemId)
                    }
                }

                val chunkAlbums = HashMap<Pair<String, Boolean>, MutableSet<Long>>()
                toInsert.forEachIndexed { index, media ->
                    val albumName = media.path.split('/').lastOrNull { it.isNotEmpty() }
                    albumName?.let {
                        val rawId = rawMediaIds[index]
                        val realId = if (rawId > 0) rawId else mediaDao.getMediaId(
                            media.storageId,
                            media.storageItemId
                        ) ?: -1L
                        if (realId > 0) {
                            val key = albumName to messengerAlbums.isMessengerMedia(media)
                            chunkAlbums.getOrPut(key) { mutableSetOf() } += realId
                        }
                    }
                }

                chunkAlbums.forEach { (key, ids) ->
                    val album = messengerAlbums.albumFor(key.first, key.second)
                    addToAlbum(ids, album.id)
                }

                updated += validMediaIds

                CoroutineScope(Dispatchers.IO).launch {
                    val batch = toInsert.mapIndexedNotNull { index, media ->
                        val rawId = rawMediaIds[index]
                        val realId = if (rawId > 0) rawId else mediaDao.getMediaId(media.storageId, media.storageItemId) ?: -1L
                        if (realId > 0) realId to media.uri else null
                    }
                    ThumbnailCache.preloadBatch(context, batch)
                }
            }
        }

        mediaDao.deleteAllExcept(provider.storage.id, updated)
        repairFolderAlbumsOnce(provider.storage.id)
        mergeDuplicateAlbumsOnce()
        albumDao.deleteEmptyAlbums()
    }

    /**
     * Раньше новые файлы искали альбом папки только в «Прочее», и если альбом
     * перенесли в другую категорию, в «Прочее» появлялся его дубль.
     */
    private suspend fun mergeDuplicateAlbumsOnce() {
        if (prefs.getBoolean(KEY_DUPLICATES_MERGED, false)) return

        albumDao.getDuplicateAlbums().groupBy { it.name }.values.forEach { albums ->
            val target = albums.first()
            albums.drop(1)
                .filter { AlbumCategory.OTHER.id == it.categoryId }
                .forEach { albumDao.mergeAlbums(it.id, target.id) }
        }

        prefs.edit { putBoolean(KEY_DUPLICATES_MERGED, true) }
    }

    /**
     * Сканирование, прерванное ошибкой, оставляло файлы в базе без альбомов,
     * а повторные сканирования такие файлы не трогают. Восстанавливаем один раз,
     * чтобы не возвращать в альбомы файлы, которые пользователь убрал сам.
     */
    private suspend fun repairFolderAlbumsOnce(storageId: Long) {
        val prefsKey = "$KEY_ALBUMS_REPAIRED$storageId"
        if (prefs.getBoolean(prefsKey, false)) return

        val albums = HashMap<Pair<String, Boolean>, MutableSet<Long>>()
        mediaDao.getMediaWithoutAlbum(storageId).forEach { media ->
            val albumName = media.path.split('/').lastOrNull { it.isNotEmpty() } ?: return@forEach
            albums.getOrPut(albumName to messengerAlbums.isMessengerMedia(media)) { mutableSetOf() } += media.id
        }
        albums.forEach { (key, ids) ->
            addToAlbum(ids, messengerAlbums.albumFor(key.first, key.second).id)
        }

        prefs.edit { putBoolean(prefsKey, true) }
    }

    private companion object {
        const val PREFS_NAME = "media_service"
        const val KEY_ALBUMS_REPAIRED = "albums_repaired_"
        const val KEY_DUPLICATES_MERGED = "duplicates_merged"
    }
}
