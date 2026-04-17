package com.asinosoft.gallery.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import com.asinosoft.gallery.GalleryApp
import javax.inject.Inject
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.flow.first

class LocalMediaService
@Inject
constructor(
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
    private val repository: LocalMediaRepository
) : MediaService {
    override suspend fun delete(
        mediaIds: Collection<Long>,
        context: Context,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        val uris = mediaDao.getUris(mediaIds)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sender =
                MediaStore
                    .createDeleteRequest(
                        context.contentResolver,
                        uris
                    ).intentSender

            val request =
                IntentSenderRequest
                    .Builder(sender)
                    .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 0)
                    .build()

            launcher.launch(request)
        } else {
            throw NotImplementedError()
        }
    }

    override suspend fun postDelete(mediaIds: Collection<Long>) {
        val albums = albumDao.getMediaAlbumIds(mediaIds)
        mediaDao.deleteAll(mediaIds)
        albums.forEach { updateAlbumStats(it) }
    }

    override suspend fun edit(mediaId: Long, context: Context) {
        val uri = mediaDao.getUri(mediaId)
        val edit =
            Intent().apply {
                action = Intent.ACTION_EDIT
                data = uri
            }
        context.startActivity(edit)
    }

    override suspend fun share(mediaIds: Collection<Long>, context: Context) {
        val uris = mediaDao.getUris(mediaIds)
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

    override suspend fun addToAlbum(mediaIds: Collection<Long>, albumId: Long) {
        albumDao.addMediaToAlbum(mediaIds, albumId)
        updateAlbumStats(albumId)
    }

    override suspend fun createAlbum(name: String): Album {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Album name must not be empty" }

        val id = albumDao.upsert(Album(0, trimmed))
        return Album(id, trimmed)
    }

    override suspend fun removeFromAlbum(mediaIds: Collection<Long>, albumId: Long) {
        if (mediaIds.isEmpty()) {
            return
        }
        albumDao.removeMediaFromAlbum(mediaIds, albumId)
        updateAlbumStats(albumId)
    }

    override suspend fun update(uri: Uri) {
        Log.d(GalleryApp.TAG, "fetchOne: $uri")

        mediaDao.upsert(repository.fetchOne(uri))
    }

    override suspend fun updateAll() {
        Log.d(GalleryApp.TAG, "rescan")

        measureTimeMillis {
            val images = repository.fetchAll()
            val deletedImages =
                mediaDao
                    .getImages()
                    .first()
                    .filterNot { cached -> images.any { it.uri == cached.uri } }
                    .map { it.id }

            mediaDao.deleteAll(deletedImages)
            mediaDao.upsertAll(images)
        }.also {
            Log.i(GalleryApp.TAG, "DONE in $it ms")
        }
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
