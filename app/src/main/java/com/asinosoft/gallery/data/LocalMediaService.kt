package com.asinosoft.gallery.data

import android.app.RecoverableSecurityException
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.annotation.RequiresApi
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
    private var pendingMedia = ArrayList<Media>()
    private var pendingAlbum: String? = null

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
        mediaDao.deleteAll(media)
        albumDao.deleteAll(albumDao.getEmptyAlbums())
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

    @RequiresApi(Build.VERSION_CODES.Q)
    override suspend fun move(
        media: Collection<Media>,
        album: String,
        context: Context,
        launcher: ActivityResultLauncher<IntentSenderRequest>
    ) {
        pendingMedia.clear()
        moveInternal(media, album, context, launcher)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    override suspend fun postMove(context: Context) {
        pendingAlbum?.let { albumName ->
            moveInternal(pendingMedia, albumName, context, null)
        }
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

            val albums = mediaDao.getAlbums().first()
            val deletedAlbums =
                albumDao
                    .getAlbums()
                    .first()
                    .filterNot { cached -> albums.any { it.name == cached.name } }
            albumDao.upsertAll(albums)
            albumDao.deleteAll(deletedAlbums)
        }.also {
            Log.i(GalleryApp.TAG, "DONE in $it ms")
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private suspend fun moveInternal(
        media: Collection<Media>,
        album: String,
        context: Context,
        launcher: ActivityResultLauncher<IntentSenderRequest>?
    ) {
        val uris = media.map { it.uri.lastPathSegment }
        Log.d(GalleryApp.TAG, "Move into $album: $uris")

        val targetAlbum = album.trim()
        if (media.isEmpty() || targetAlbum.isEmpty()) {
            return
        }

        val moved = HashSet<Media>()
        val failed = HashSet<Media>()
        media.forEach { media ->
            if (media.album == targetAlbum) {
                return@forEach
            }

            val baseDir =
                if (media.mimeType.startsWith("video")) {
                    Environment.DIRECTORY_MOVIES
                } else {
                    Environment.DIRECTORY_PICTURES
                }
            val relativePath = "$baseDir/$targetAlbum/"

            try {
                val values =
                    ContentValues().apply {
                        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
                    }
                context.contentResolver.update(media.uri, values, null, null)
                moved.add(media.setAlbum(album))
            } catch (ex: RecoverableSecurityException) {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.Q && launcher != null) {
                    pendingAlbum = album
                    pendingMedia.add(media)
                    val request =
                        IntentSenderRequest
                            .Builder(ex.userAction.actionIntent)
                            .build()
                    launcher.launch(request)
                } else {
                    failed.add(media)
                }
            }
        }

        pendingMedia.removeAll(moved)
        mediaDao.upsertAll(moved)
        albumDao.deleteAll(albumDao.getEmptyAlbums())
        albumDao.upsertAll(mediaDao.getAlbums().first())

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (failed.isNotEmpty() && launcher != null) {
                pendingAlbum = album
                pendingMedia.addAll(failed)
                val sender =
                    MediaStore
                        .createWriteRequest(
                            context.contentResolver,
                            failed.map { it.uri }
                        ).intentSender
                val request =
                    IntentSenderRequest
                        .Builder(sender)
                        .setFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION, 0)
                        .build()
                launcher.launch(request)
            }
        }
    }
}
