package com.asinosoft.gallery.data

import android.content.Context
import android.content.Intent
import android.util.Log
import com.asinosoft.gallery.GalleryApp
import com.asinosoft.gallery.data.storage.StorageProvider
import com.asinosoft.gallery.di.IntentHelper
import javax.inject.Inject
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.withContext

class MediaService @Inject constructor(
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao
) {
    private val intentHelper = IntentHelper

    suspend fun add(media: Media) {
        val mediaId = mediaDao.upsert(media)
        media.bucket?.let { name ->
            val albumId = albumDao.upsert(Album(name = name))
            addToAlbum(listOf(mediaId), albumId)
        }
    }

    suspend fun delete(mediaIds: Collection<Long>, context: Context, callback: () -> Unit) {
        val uris = withContext(Dispatchers.IO) { mediaDao.getUris(mediaIds) }
        intentHelper.delete(uris, context) {
            val albums = albumDao.getMediaAlbumIds(mediaIds)
            mediaDao.deleteAll(mediaIds)
            albums.forEach { updateAlbumStats(it) }
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
        updateAlbumStats(albumId)
    }

    suspend fun createAlbum(name: String): Album {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Album name must not be empty" }

        val id = albumDao.upsert(Album(0, trimmed))
        return Album(id, trimmed)
    }

    suspend fun removeFromAlbum(mediaIds: Collection<Long>, albumId: Long) {
        if (mediaIds.isEmpty()) {
            return
        }
        albumDao.removeMediaFromAlbum(mediaIds, albumId)
        updateAlbumStats(albumId)
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
        val albums = HashMap<String, MutableSet<Long>>()
        provider.fetchAll().chunked(100).collect { fetched ->
            val media = mediaDao.getMediaByStorageItemIds(
                provider.storage.id,
                fetched.map {
                    it.storageItemId
                }
            )
            updated += media.map { it.id }

            val uris = media.map { it.uri }.toSet()
            val toInsert = fetched.filterNot { uris.contains(it.uri) }

            if (toInsert.isNotEmpty()) {
                val mediaIds = mediaDao.upsertAll(toInsert)
                toInsert.forEachIndexed { index, media ->
                    media.bucket?.let { name ->
                        val mediaId = mediaIds[index]
                        val album = albums.getOrPut(name, { mutableSetOf() })
                        album += mediaId
                    }
                }

                updated += mediaIds
            }
        }

        albums.forEach { (name, mediaIds) ->
            val albumId =
                albumDao.getAlbumByName(name)?.id ?: albumDao.upsert(Album(name = name))
            addToAlbum(mediaIds, albumId)
        }
        mediaDao.deleteAllExcept(provider.storage.id, updated)
        albumDao.deleteEmptyAlbums()
    }

    private suspend fun updateAlbumStats(albumId: Long) {
        val stats = albumDao.getAlbumStats(albumId)
        if (stats.count > 0) {
            albumDao.upsert(
                Album(
                    id = albumId,
                    name = stats.name,
                    size = stats.size,
                    count = stats.count,
                    cover = stats.cover,
                    date = stats.date
                )
            )
        } else {
            albumDao.delete(albumId)
        }
    }
}
