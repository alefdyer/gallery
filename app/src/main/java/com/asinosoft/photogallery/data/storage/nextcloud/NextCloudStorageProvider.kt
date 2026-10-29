package com.asinosoft.photogallery.data.storage.nextcloud

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.asinosoft.photogallery.data.Image
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.Video
import com.asinosoft.photogallery.data.mediaParentPath
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.data.storage.StorageCheckResult
import com.asinosoft.photogallery.data.storage.StorageProvider
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.ArrayDeque
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import okhttp3.Credentials
import okhttp3.Request

class NextCloudStorageProvider(override val storage: Storage) : StorageProvider {
    private val connection = OkHttpSardine().apply {
        setCredentials(storage.login, storage.password)
    }
    private val rootPath: String
        get() = "/remote.php/dav/files/${storage.login}/"

    override fun authorize(request: Request): Request {
        if (null == storage.login || null == storage.password) return request

        return request.newBuilder()
            .header("Authorization", Credentials.basic(storage.login, storage.password))
            .build()
    }

    override suspend fun checkConnection(): StorageCheckResult = try {
        Log.i("nextcloud", "Check: ${storage.url}")
        connection.list(buildUrl("/remote.php/dav/files/${storage.login}/"), 0)
        StorageCheckResult.Success
    } catch (ex: Throwable) {
        Log.e("nextcloud", "Check error: $ex")
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
        Log.i("nextcloud", "fetchAll")
        val queue = ArrayDeque<String>()
        queue.addLast(buildUrl("/remote.php/dav/files/${storage.login}/"))
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            Log.d("nextcloud", "Fetch: $path")
            val items = try {
                connection.list(path)
            } catch (ex: Throwable) {
                Log.e("nextcloud", "List failed: $path", ex)
                continue
            }
            val subdirs = ArrayList<String>()
            for (item in items) {
                if (path.endsWith(item.path)) continue
                if (item.isDirectory) {
                    subdirs += buildUrl(item.path)
                    continue
                }
                if (!item.contentType.startsWith("image/") &&
                    !item.contentType.startsWith("video/")
                ) {
                    continue
                }
                val datetime =
                    item.modified?.toInstant()?.atZone(ZoneId.systemDefault())
                        ?: ZonedDateTime.now()
                val isImage = item.contentType.startsWith("image/")
                emit(
                    Media(
                        id = 0,
                        uri = buildUrl(item.href.toString()).toUri(),
                        date = datetime.toLocalDate(),
                        time = datetime.toLocalTime(),
                        path = mediaParentPath(item.path, rootPath),
                        size = item.contentLength,
                        filename = item.name,
                        mimeType = item.contentType,
                        storageId = storage.id,
                        storageType = storage.type,
                        storageItemId = item.path,
                        thumbnail = previewUri(item.path),
                        image = Image(
                            width = 0,
                            height = 0,
                            orientation = 0
                        ).takeIf { isImage },
                        video = Video(duration = 0).takeIf { !isImage }
                    )
                )
            }
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

    override suspend fun getThumbnailUri(media: Media): Uri =
        media.thumbnail ?: media.uri!!

    private fun previewUri(davPath: String): Uri? {
        val login = storage.login ?: return null
        val prefix = "/remote.php/dav/files/$login"
        val relative = when {
            davPath.startsWith(prefix) -> davPath.removePrefix(prefix).ifEmpty { "/" }
            else -> davPath
        }
        val fileParam = URLEncoder.encode(relative, Charsets.UTF_8.name())
        return buildUrl("/index.php/core/preview.png?file=$fileParam&x=512&y=512&a=1").toUri()
    }

    private fun buildUrl(path: String): String {
        if (path.startsWith("http://") || path.startsWith("https://")) {
            return path
        }

        val base = requireNotNull(storage.url) {
            "WebDAV storage.url must be configured."
        }
        return (if (path.startsWith('/')) "$base$path" else "$base/$path")
    }
}
