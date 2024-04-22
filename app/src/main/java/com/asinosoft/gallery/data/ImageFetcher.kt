package com.asinosoft.gallery.data

import android.util.Log
import javax.inject.Inject
import kotlin.system.measureTimeMillis

const val TAG = "gallery.fetcher"

class ImageFetcher @Inject constructor(
    private val albumDao: AlbumDao,
    private val imageDao: ImageDao,
    private val repository: LocalImageRepository,
) {
    suspend fun fetchAll() {
        Log.d(TAG, "fetchAll")

        measureTimeMillis {
            val images = repository.fetchAll()
            val deleted = imageDao.getImages()
                .filterNot { cached -> images.any { it.path == cached.path } }

            imageDao.deleteAll(deleted)
            imageDao.upsertAll(images)

            val albums = imageDao.getAlbums()
            albumDao.upsertAll(albums)
        }.also {
            Log.i(TAG, "DONE in $it ms")
        }
    }

    suspend fun fetchOne(path: String) {
        Log.d(TAG, "fetchOne: $path")

        imageDao.upsert(repository.fetchOne(path))
    }
}
