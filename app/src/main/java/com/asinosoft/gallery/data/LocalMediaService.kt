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
import java.util.UUID
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
        media: Collection<Media>,
        context: Context,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        Log.d(GalleryApp.TAG, "Delete ${media.count()} images")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val sender =
                MediaStore
                    .createDeleteRequest(
                        context.contentResolver,
                        media.map { it.uri }
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

    override suspend fun postDelete(media: Collection<Media>) {
        val albums = albumDao.getMediaAlbums(media.map { it.id })
        mediaDao.deleteAll(media)
        albums.forEach { updateAlbumStats(it) }
    }

    override suspend fun edit(media: Media, context: Context) {
        Log.d(GalleryApp.TAG, "Edit ${media.uri}")
        val edit =
            Intent().apply {
                action = Intent.ACTION_EDIT
                data = media.uri
            }
        context.startActivity(edit)
    }

    override suspend fun share(media: Collection<Media>, context: Context) {
        Log.d(GalleryApp.TAG, "Share ${media.count()} images")
        val send =
            if (1 == media.size) {
                Intent().apply {
                    action = Intent.ACTION_SEND
                    type = "image/jpeg"
                    putExtra(Intent.EXTRA_STREAM, media.first().uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } else {
                Intent().apply {
                    action = Intent.ACTION_SEND_MULTIPLE
                    type = "image/jpeg"

                    putParcelableArrayListExtra(
                        Intent.EXTRA_STREAM,
                        media.map { it.uri }.toCollection(ArrayList())
                    )
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            }
        val chooser = Intent.createChooser(send, null)
        context.startActivity(chooser)
    }

    override suspend fun addToAlbum(media: Collection<Media>, album: Album) {
        albumDao.addMediaToAlbum(media, album)
        updateAlbumStats(album)
    }

    override suspend fun createAlbum(name: String): Album {
        val trimmed = name.trim()
        require(trimmed.isNotEmpty()) { "Album name must not be empty" }
        val id = UUID.randomUUID()

        return Album(
            id = id,
            name = trimmed
        ).also { albumDao.upsert(it) }
    }

    override suspend fun removeFromAlbum(media: Collection<Media>, album: Album) {
        if (media.isEmpty()) {
            return
        }
        albumDao.removeMediaFromAlbum(album.id, media.map { it.id })
        updateAlbumStats(album)
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

            mediaDao.deleteAll(deletedImages)
            mediaDao.upsertAll(images)
        }.also {
            Log.i(GalleryApp.TAG, "DONE in $it ms")
        }
    }

    private suspend fun updateAlbumStats(album: Album) {
        val stats = albumDao.getAlbumStats(album.id, album.cover)
        albumDao.upsert(
            Album(
                id = album.id,
                name = album.name,
                size = stats.size,
                count = stats.count,
                cover = stats.cover,
                date = stats.date
            )
        )
    }
}
