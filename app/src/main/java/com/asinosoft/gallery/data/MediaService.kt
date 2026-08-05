package com.asinosoft.gallery.data

import android.content.Context
import android.content.Intent
import android.util.Log
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
    @param:ApplicationContext private val context: Context
) {
    private val intentHelper = IntentHelper

    suspend fun add(media: Media) {
        var mediaId = mediaDao.upsert(media)
        if (mediaId <= 0) {
            mediaId = mediaDao.getMediaId(media.storageId, media.storageItemId) ?: return
        }
        val albumName = media.path.split('/').last { it.isNotEmpty() }
        val album = albumDao.getOrCreateAlbum(albumName, AlbumCategory.OTHER.id)
        addToAlbum(listOf(mediaId), album.id)

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

                val chunkAlbums = HashMap<String, MutableSet<Long>>()
                toInsert.forEachIndexed { index, media ->
                    val albumName = media.path.split('/').last { it.isNotEmpty() }
                    val rawId = rawMediaIds[index]
                    val realId = if (rawId > 0) rawId else mediaDao.getMediaId(media.storageId, media.storageItemId) ?: -1L
                    if (realId > 0) {
                        chunkAlbums.getOrPut(albumName) { mutableSetOf() } += realId
                    }
                }

                chunkAlbums.forEach { (name, ids) ->
                    val album = albumDao.getOrCreateAlbum(name, AlbumCategory.OTHER.id)
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

        val existingIds = mediaDao.getAllIds(provider.storage.id).toSet()
        val toDelete = existingIds - updated
        if (toDelete.isNotEmpty()) {
            mediaDao.deleteAll(toDelete)
        }
        albumDao.deleteEmptyAlbums()
    }
}
