package com.asinosoft.gallery.data

import kotlinx.coroutines.flow.first
import java.util.logging.Logger
import javax.inject.Inject
import kotlin.system.measureTimeMillis

class ImageFetcher @Inject constructor(
    private val imageDao: ImageDao,
    private val repository: LocalImageRepository,
) {
    private val logger = Logger.getLogger(javaClass.simpleName)

    suspend fun fetchAll() {
        logger.info("START")

        measureTimeMillis {
            val images = repository.fetchAll()
            val deleted = imageDao.getImages().first()
                .filterNot { cached -> images.any { it.path == cached.path } }

            imageDao.deleteAll(deleted)
            imageDao.upsertAll(images)
        }.also {
            logger.info("FINISH in $it ms")
        }
    }

    suspend fun fetchOne(path: String) {
        imageDao.upsert(repository.fetchOne(path))
    }
}
