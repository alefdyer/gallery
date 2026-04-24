package com.asinosoft.gallery.data.storage.webdav

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.Video
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageProvider
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import okhttp3.Credentials
import okhttp3.Request

class WebDavStorageProvider(override val storage: Storage) : StorageProvider {
    private val webdav = OkHttpSardine().apply {
        setCredentials(storage.username, storage.secret)
    }

    override fun authorize(request: Request): Request {
        if (null == storage.username || null == storage.secret) return request

        return request.newBuilder()
            .header("Authorization", Credentials.basic(storage.username, storage.secret))
            .build()
    }

    override suspend fun fetchAll(): Flow<Media> = flow {
        Log.i("webdav", "fetchAll")
        emitAll(fetch(buildAbsoluteDavUrl(storage.rootPath ?: "/")))
    }

    override suspend fun fetchOne(uri: Uri): Media? {
        TODO("Not yet implemented")
    }

    private fun fetch(path: String): Flow<Media> = flow {
        Log.d("webdav", "Fetch: $path")
        webdav.list(path).forEach {
            Log.d("webdav", "Found: $it|${it.path}")
            if (path.endsWith(it.path)) return@forEach

            if (it.isDirectory) {
                emitAll(fetch(buildAbsoluteDavUrl(it.path)))
            } else if (it.contentType.startsWith("image/") || it.contentType.startsWith("video/")) {
                Log.d("webdav", "Add: [${storage.id}, ${it.path}]")
                val datetime =
                    it.modified?.toInstant()?.atZone(ZoneId.systemDefault()) ?: ZonedDateTime.now()
                val isImage = it.contentType.startsWith("image/")
                emit(
                    Media(
                        id = 0,
                        uri = buildAbsoluteDavUrl(it.href.toString()).toUri(),
                        date = datetime.toLocalDate(),
                        time = datetime.toLocalTime(),
                        bucket = it.path.substringAfterLast('/'),
                        size = it.contentLength,
                        filename = it.name,
                        mimeType = it.contentType,
                        storageId = storage.id,
                        storageItemId = it.path,
                        image = Image(
                            width = 0,
                            height = 0,
                            orientation = 0
                        ).takeIf { isImage },
                        video = Video(duration = 0).takeIf { !isImage }
                    )
                )
            }
        }
    }

    private fun buildAbsoluteDavUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }

        val base = requireNotNull(storage.url) {
            "WebDAV storage.url must be configured."
        }.trimEnd('/')
        return (if (path.startsWith('/')) "$base$path" else "$base/$path")
    }
}
