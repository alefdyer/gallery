package com.asinosoft.gallery.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.asinosoft.gallery.data.storage.StorageProviderRegistry
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ThumbnailPrefetchWorker(appContext: Context, params: WorkerParameters) :
    CoroutineWorker(appContext, params) {
    companion object {
        const val KEY_MEDIA_IDS = "mediaIds"
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val mediaIds = inputData.getLongArray(KEY_MEDIA_IDS)?.toList().orEmpty()
        if (mediaIds.isEmpty()) return@withContext Result.success()

        val db = AppDatabase.getInstance(applicationContext)
        val mediaDao = db.imageDao()
        val storageProviderRegistry = StorageProviderRegistry(db.storageDao(), applicationContext)
        val imageLoader = SingletonImageLoader.get(applicationContext)
        val size = applicationContext.resources.displayMetrics.widthPixels

        mediaDao.getByIds(mediaIds).forEach { media ->
            if (media.filename.endsWith(".gif", ignoreCase = true)) {
                return@forEach
            }

            val thumbnailFile = File(applicationContext.cacheDir, media.id.toString())
            if (thumbnailFile.exists()) {
                return@forEach
            }

            runCatching {
                val thumbnailUri =
                    storageProviderRegistry.getStorageProvider(media.storageId)
                        .getThumbnailUri(media)

                val request = ImageRequest.Builder(applicationContext)
                    .data(thumbnailUri)
                    .size(size)
                    .memoryCacheKey("media#${media.id}")
                    .diskCacheKey("media#${media.id}")
                    .allowHardware(false)
                    .build()

                val result = imageLoader.execute(request)
                thumbnailFile.outputStream().use { out ->
                    result.image?.toBitmap()?.compress(
                        android.graphics.Bitmap.CompressFormat.WEBP,
                        90,
                        out
                    )
                }
            }
        }

        Result.success()
    }
}
