package com.asinosoft.gallery.data

import android.net.Uri
import android.util.Log
import com.asinosoft.gallery.GalleryApp
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.system.measureTimeMillis

class ImageFetcher
    @Inject
    constructor(
        private val albumDao: AlbumDao,
        private val mediaDao: MediaDao,
        private val repository: LocalMediaRepository,
    ) {
        suspend fun fetchAll() {
            Log.d(GalleryApp.TAG, "fetchAll")

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

        suspend fun fetchOne(uri: Uri) {
            Log.d(GalleryApp.TAG, "fetchOne: $uri")

            mediaDao.upsert(repository.fetchOne(uri))
        }
    }
