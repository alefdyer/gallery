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
            val fetched = repository.fetchAll()

            val cached = imageDao.getImages().first()

            val toDelete = cached.filterNot { image -> fetched.any { it.path == image.path } }
            val toInsert = fetched.filterNot { image -> cached.any { it.path == image.path } }

            imageDao.deleteAll(toDelete)
            imageDao.upsertAll(toInsert)
        }.also {
            logger.info("FINISH in $it ms")
        }
    }

    suspend fun fetchOne(path: String) {
        imageDao.upsert(repository.fetchOne(path))
    }
}
