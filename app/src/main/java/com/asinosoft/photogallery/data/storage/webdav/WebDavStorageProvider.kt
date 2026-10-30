package com.asinosoft.photogallery.data.storage.webdav

import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.core.net.toUri
import com.asinosoft.photogallery.data.Image
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.Video
import com.asinosoft.photogallery.data.mediaParentPath
import com.asinosoft.photogallery.data.mediaSortDateTime
import com.asinosoft.photogallery.data.parseTakenAtFromFilename
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.data.storage.StorageCheckResult
import com.asinosoft.photogallery.data.storage.StorageProvider
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.ArrayDeque
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import okhttp3.Credentials
import okhttp3.Request

class WebDavStorageProvider(override val storage: Storage) : StorageProvider {
    private val webdav = OkHttpSardine().apply {
        setCredentials(storage.login, storage.password)
    }
    private val rootPath: String by lazy {
        runCatching { URI(rootUrl()).path }.getOrNull().orEmpty()
    }

    override fun authorize(request: Request): Request {
        if (null == storage.login || null == storage.password) return request

        return request.newBuilder()
            .header("Authorization", Credentials.basic(storage.login, storage.password))
            .build()
    }

    override suspend fun checkConnection(): StorageCheckResult = try {
        // Depth 0 — только сама коллекция, без детей и без скачивания тела.
        webdav.list(rootUrl(), 0)
        StorageCheckResult.Success
    } catch (ex: Throwable) {
        when {
            ex is UnknownHostException ||
                ex is ConnectException ||
                ex is SocketTimeoutException ->
                StorageCheckResult.ServerNotFound

            ex.message?.contains("401", ignoreCase = true) == true ||
                ex.message?.contains("403", ignoreCase = true) == true ||
                ex.message?.contains("unauthorized", ignoreCase = true) == true ||
                ex.message?.contains("forbidden", ignoreCase = true) == true ->
                StorageCheckResult.AuthorizationFailed

            else -> StorageCheckResult.UnknownError(ex.message)
        }
    }

    override suspend fun fetchAll(): Flow<Media> = flow {
        Log.i("webdav", "fetchAll")
        // Итеративный обход: в памяти только очередь путей папок, не стек Flow/списков.
        val queue = ArrayDeque<String>()
        queue.addLast(rootUrl())
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            Log.d("webdav", "Fetch: $path")
            val folderPath = runCatching { URI(path).path }.getOrNull()?.trimEnd('/')
            val items = try {
                webdav.list(path)
            } catch (ex: Throwable) {
                Log.e("webdav", "List failed: $path", ex)
                continue
            }
            // Сначала файлы (emit), пути подпапок — в очередь; список PROPFIND отпускаем.
            val subdirs = ArrayList<String>()
            for (item in items) {
                if (folderPath == item.path.trimEnd('/')) continue
                if (item.isDirectory) {
                    subdirs += resolve(item.href)
                    continue
                }
                val mimeType = mimeTypeOf(item.contentType, item.name)
                if (!mimeType.startsWith("image/") && !mimeType.startsWith("video/")) continue

                val modifiedAt = item.modified?.toInstant()?.toEpochMilli()?.takeIf { it > 0L }
                // PROPFIND не отдаёт EXIF; съёмка — из имени (IMG_20260924_141005) или позже из EXIF.
                val takenAt = parseTakenAtFromFilename(item.name)
                val (sortDate, sortTime) = mediaSortDateTime(takenAt, modifiedAt, null)
                val isImage = mimeType.startsWith("image/")
                emit(
                    Media(
                        uri = resolve(item.href).toUri(),
                        date = sortDate,
                        time = sortTime,
                        path = mediaParentPath(item.path, rootPath),
                        size = item.contentLength,
                        filename = item.name,
                        mimeType = mimeType,
                        storageId = storage.id,
                        storageType = storage.type,
                        storageItemId = item.path,
                        image = Image().takeIf { isImage },
                        video = Video().takeIf { !isImage },
                        takenAt = takenAt,
                        modifiedAt = modifiedAt,
                    )
                )
            }
            // DFS: сначала глубже по первой подпапке (как раньше через emitAll).
            for (i in subdirs.indices.reversed()) {
                queue.addFirst(subdirs[i])
            }
            yield()
        }
    }

    override suspend fun fetchOne(uri: Uri): Media? {
        TODO("Not yet implemented")
    }

    override suspend fun getMediaUri(media: Media): Uri = media.uri!!

    override suspend fun getThumbnailUri(media: Media): Uri = media.uri!!

    private fun mimeTypeOf(contentType: String?, name: String?): String {
        val extension = name?.substringAfterLast('.', "")?.lowercase().orEmpty()
        val fromName = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension)
        // Расширение надёжнее: серверы WebDAV часто отдают video/mpeg / application/octet-stream.
        if (fromName != null &&
            (fromName.startsWith("image/") || fromName.startsWith("video/"))
        ) {
            return fromName
        }
        if (null != contentType && (contentType.startsWith("image/") || contentType.startsWith("video/"))) {
            return contentType
        }
        return contentType ?: ""
    }

    private fun rootUrl(): String {
        val base = requireNotNull(storage.url) {
            "WebDAV storage.url must be configured."
        }
        return base.toString().trimEnd('/') + "/"
    }

    private fun resolve(href: URI): String = URI(rootUrl()).resolve(href).toString()
}
