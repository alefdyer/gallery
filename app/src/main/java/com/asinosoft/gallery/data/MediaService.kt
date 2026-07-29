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
        val mediaId = mediaDao.upsert(media)
        val albumName = media.path.split('/').last { it.isNotEmpty() }
        val album = albumDao.getOrCreateAlbum(albumName, AlbumCategory.OTHER.id)
        addToAlbum(listOf(mediaId), album.id)
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
                val mediaIds = mediaDao.upsertAll(toInsert)
                val chunkAlbums = HashMap<String, MutableSet<Long>>()
                toInsert.forEachIndexed { index, media ->
                    val albumName = media.path.split('/').last { it.isNotEmpty() }
                    val mediaId = mediaIds[index]
                    chunkAlbums.getOrPut(albumName) { mutableSetOf() } += mediaId
                }

                chunkAlbums.forEach { (name, ids) ->
                    val album = albumDao.getOrCreateAlbum(name, AlbumCategory.OTHER.id)
                    addToAlbum(ids, album.id)
                }

                updated += mediaIds

                CoroutineScope(Dispatchers.IO).launch {
                    val batch = toInsert.mapIndexed { index, media ->
                        mediaIds[index] to media.uri
                    }
                    ThumbnailCache.preloadBatch(context, batch)
                }
            }
        }

        mediaDao.deleteAllExcept(provider.storage.id, updated)
        albumDao.deleteEmptyAlbums()
    }
}
