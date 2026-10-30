package com.asinosoft.photogallery.data

import android.util.Log
import com.asinosoft.photogallery.GalleryApp
import com.asinosoft.photogallery.data.storage.StorageAuthProvider
import com.asinosoft.photogallery.data.storage.StorageProviderRegistry
import com.asinosoft.photogallery.data.storage.StorageService
import java.io.File
import java.io.FileOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.Request
import kotlin.coroutines.coroutineContext

/**
 * Полная закачка remote-видео в [VlcMediaCache] с прогрессом (кнопка «Загрузить»).
 */
object RemoteVideoDownloader {
    private const val TAG = "RemoteVideoDownload"

    /** Лимит для явной закачки пользователем (выше кэша LibVLC). */
    const val MAX_BYTES = 2L * 1024 * 1024 * 1024

    fun cachedOrNull(app: GalleryApp, media: Media): File? {
        val file = VlcMediaCache.fileFor(app, media.id, media.filename)
        if (!file.exists() || file.length() <= 0L) return null
        // WebDAV size в БД иногда не совпадает с Content-Length — допускаем небольшой разброс.
        if (media.size > 0L) {
            val delta = kotlin.math.abs(file.length() - media.size)
            if (delta > 0L && delta > media.size / 50L && delta > 256L * 1024L) {
                return null
            }
        }
        VlcMediaCache.touch(file)
        return file
    }

    /**
     * @param onProgress 0f..1f, или без вызова если размер неизвестен
     * @return локальный файл
     */
    suspend fun download(
        app: GalleryApp,
        storageService: StorageService,
        storageProviderRegistry: StorageProviderRegistry,
        media: Media,
        onProgress: (Float) -> Unit = {},
    ): File = withContext(Dispatchers.IO) {
        cachedOrNull(app, media)?.let { return@withContext it }

        if (media.size > MAX_BYTES) {
            error("File too large (${media.size} bytes)")
        }

        checkNotNull(storageService.ensureFreshAuth(media.storageId)) {
            "Waiting for Google sign-in"
        }
        storageProviderRegistry.invalidate(media.storageId)
        val provider = storageProviderRegistry.getStorageProvider(media.storageId)
        val remoteUri = provider.getMediaUri(media)

        val out = VlcMediaCache.fileFor(app, media.id, media.filename)
        VlcMediaCache.trim(app)
        val tmp = File(out.parentFile, "${out.name}.part")
        tmp.delete()

        val request = Request.Builder()
            .url(remoteUri.toString())
            .get()
            .header(StorageAuthProvider.HEADER_STORAGE_ID, media.storageId.toString())
            .build()
        Log.i(TAG, "Download media=${media.id} → ${out.name}")

        app.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) error("Download HTTP ${response.code}")
            val body = response.body ?: error("Empty body")
            val total = response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
                ?: media.size.takeIf { it > 0 }
            if (total != null && total > MAX_BYTES) {
                error("File too large ($total bytes)")
            }
            FileOutputStream(tmp).use { output ->
                body.byteStream().use { input ->
                    val buf = ByteArray(64 * 1024)
                    var read = 0L
                    var lastPct = -1
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        output.write(buf, 0, n)
                        read += n
                        if (total != null && total > 0) {
                            val pct = ((read * 100L) / total).toInt().coerceIn(0, 100)
                            if (pct != lastPct) {
                                lastPct = pct
                                onProgress(pct / 100f)
                            }
                        }
                    }
                }
            }
            if (!tmp.exists() || tmp.length() <= 0L) {
                tmp.delete()
                error("Empty download")
            }
            if (total != null && tmp.length() != total) {
                Log.w(TAG, "Size mismatch got=${tmp.length()} expected=$total")
            }
        }

        if (out.exists()) out.delete()
        if (!tmp.renameTo(out)) {
            tmp.copyTo(out, overwrite = true)
            tmp.delete()
        }
        VlcMediaCache.touch(out)
        VlcMediaCache.trim(app, keep = out)
        onProgress(1f)
        Log.i(TAG, "Downloaded ${out.length()} bytes media=${media.id}")
        out
    }
}
