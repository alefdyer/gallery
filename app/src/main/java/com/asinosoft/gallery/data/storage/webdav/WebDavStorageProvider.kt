package com.asinosoft.gallery.data.storage.webdav

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.Video
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageCheckResult
import com.asinosoft.gallery.data.storage.StorageProvider
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import okhttp3.Credentials
import okhttp3.Request

class WebDavStorageProvider(override val storage: Storage) : StorageProvider {
    private val webdav = OkHttpSardine().apply {
        setCredentials(storage.login, storage.password)
    }

    override fun authorize(request: Request): Request {
        if (null == storage.login || null == storage.password) return request

        return request.newBuilder()
            .header("Authorization", Credentials.basic(storage.login, storage.password))
            .build()
    }

    override suspend fun checkConnection(): StorageCheckResult = try {
        webdav.get(buildAbsoluteDavUrl("/"))
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
        emitAll(fetch(buildAbsoluteDavUrl("/")))
    }

    override suspend fun fetchOne(uri: Uri): Media? {
        TODO("Not yet implemented")
    }

    override suspend fun getMediaUri(media: Media): Uri = media.uri

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
                        path = it.path,
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
        }
        return (if (path.startsWith('/')) "$base$path" else "$base/$path")
    }
}
