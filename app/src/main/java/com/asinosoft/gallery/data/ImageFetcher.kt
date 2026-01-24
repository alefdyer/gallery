package com.asinosoft.gallery.data

import android.util.Log
import com.asinosoft.gallery.GalleryApp
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import kotlin.system.measureTimeMillis

class ImageFetcher
    @Inject
    constructor(
        private val albumDao: AlbumDao,
        private val imageDao: ImageDao,
        private val repository: LocalImageRepository,
    ) {
        suspend fun fetchAll() {
            Log.d(GalleryApp.TAG, "fetchAll")

            measureTimeMillis {
                val images = repository.fetchAll()
                val deletedImages =
                    imageDao
                        .getImages()
                        .first()
                        .filterNot { cached -> images.any { it.path == cached.path } }

                imageDao.deleteAll(deletedImages)
                imageDao.upsertAll(images)

                val albums = imageDao.getAlbums().first()
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

        suspend fun fetchOne(path: String) {
            Log.d(GalleryApp.TAG, "fetchOne: $path")

            imageDao.upsert(repository.fetchOne(path))
        }
    }
