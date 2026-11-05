package com.asinosoft.photogallery.data.storage.nextcloud

import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.asinosoft.photogallery.data.Image
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.Video
import com.asinosoft.photogallery.data.mediaParentPath
import com.asinosoft.photogallery.data.mediaSortDateTime
import com.asinosoft.photogallery.data.parseTakenAtFromFilename
import com.asinosoft.photogallery.data.storage.FolderSnapshot
import com.asinosoft.photogallery.data.storage.FolderListingResult
import com.asinosoft.photogallery.data.storage.FolderListingScan
import com.asinosoft.photogallery.data.storage.FolderScanState
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.data.storage.StorageChange
import com.asinosoft.photogallery.data.storage.StorageCheckResult
import com.asinosoft.photogallery.data.storage.StorageProvider
import com.asinosoft.photogallery.data.storage.SyncCursorExpiredException
import com.asinosoft.photogallery.data.storage.webdav.DavDepthStamp
import com.asinosoft.photogallery.data.storage.webdav.DavSyncCollection
import com.thegrizzlylabs.sardineandroid.impl.OkHttpSardine
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import java.util.ArrayDeque
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.yield
import okhttp3.Credentials
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import okhttp3.Request

class NextCloudStorageProvider(override val storage: Storage) : StorageProvider, FolderListingScan {
    private val http = OkHttpClient.Builder()
        .dispatcher(Dispatcher().apply { maxRequestsPerHost = 32 })
        .build()
    private val connection = OkHttpSardine(http).apply {
        setCredentials(storage.login, storage.password)
    }
    private val rootPath: String
        get() = "/remote.php/dav/files/${storage.login}/"
    private var pendingCursor: String? = null
    private val folderScan = FolderScanState()
    private var fastStampCheck = true

    override fun setKnownFolders(folders: List<FolderSnapshot>) {
        folderScan.begin(folders)
    }

    override fun setFastStampCheck(enabled: Boolean) {
        fastStampCheck = enabled
    }

    override fun takeFolderListing(): FolderListingResult? = folderScan.take()

    override val supportsIncrementalSync: Boolean = true

    override fun takeSyncCursor(): String? = pendingCursor.also { pendingCursor = null }

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
        val root = buildUrl("/remote.php/dav/files/${storage.login}/")
        val sync = runCatching {
            DavSyncCollection.fetchAllChanges(
                client = http,
                authorize = ::authorize,
                rootUrl = root,
                storage = storage,
                rootPathPrefix = rootPath,
                syncToken = null,
                thumbnailUri = ::previewUri,
            )
        }.getOrNull()
        if (sync != null) {
            pendingCursor = sync.second
            for (change in sync.first) {
                if (change is StorageChange.Upsert) emit(change.media)
            }
            return@flow
        }
        pendingCursor = null
        emitAll(propfindAll())
    }

    override suspend fun fetchChanges(cursor: String): Flow<StorageChange> = flow {
        val root = buildUrl("/remote.php/dav/files/${storage.login}/")
        val sync = DavSyncCollection.fetchAllChanges(
            client = http,
            authorize = ::authorize,
            rootUrl = root,
            storage = storage,
            rootPathPrefix = rootPath,
            syncToken = cursor,
            thumbnailUri = ::previewUri,
        ) ?: throw SyncCursorExpiredException("Nextcloud sync-collection unavailable")
        pendingCursor = sync.second
        for (change in sync.first) emit(change)
    }

    private fun propfindAll(): Flow<Media> = flow {
        Log.i("nextcloud", "fetchAll")
        val queue = ArrayDeque<String>()
        val root = buildUrl("/remote.php/dav/files/${storage.login}/")
        queue.addLast(root)
        val stampByUrl = if (fastStampCheck) {
            DavDepthStamp.load(http, storage.login, storage.password, stampUrls(root))
        } else {
            emptyMap()
        }
        while (queue.isNotEmpty()) {
            val path = queue.removeFirst()
            val folderUrlPath = runCatching { java.net.URI(path).path }.getOrNull()
            val folderKey = FolderScanState.folderKey(folderUrlPath, rootPath)
            val savedStamp = folderScan.savedStamp(folderKey)
            if (fastStampCheck && !savedStamp.isNullOrBlank()) {
                val stamp = stampByUrl[path] ?: try {
                    FolderScanState.dirStamp(connection.list(path, 0).firstOrNull())
                } catch (ex: Throwable) {
                    Log.e("nextcloud", "List failed: $path", ex)
                    throw ex
                }
                if (folderScan.skipIfSameStamp(folderKey, stamp)) {
                    Log.d("nextcloud", "Folder time unchanged: $folderKey")
                    folderScan.savedChildUrls(folderKey).forEach { queue.addLast(it) }
                    continue
                }
            }
            Log.d("nextcloud", "Fetch: $path")
            val items = try {
                connection.list(path)
            } catch (ex: Throwable) {
                Log.e("nextcloud", "List failed: $path", ex)
                throw ex
            }
            val children = items.filter { !path.endsWith(it.path) }
            val self = items.firstOrNull { path.endsWith(it.path) }
            val subdirs = ArrayList<String>()
            for (item in children) {
                if (item.isDirectory) subdirs += buildUrl(item.path)
            }
            val unchanged = folderScan.onFolder(
                folderKey,
                children.map { FolderScanState.davPart(it) },
                FolderScanState.dirStamp(self),
                subdirs,
            )
            if (unchanged) Log.d("nextcloud", "Folder unchanged: $folderKey")
            for (item in children) {
                if (item.isDirectory) continue
                if (unchanged) continue
                if (!item.contentType.startsWith("image/") &&
                    !item.contentType.startsWith("video/")
                ) {
                    continue
                }
                val modifiedAt = item.modified?.toInstant()?.toEpochMilli()?.takeIf { it > 0L }
                val takenAt = parseTakenAtFromFilename(item.name)
                val (sortDate, sortTime) = mediaSortDateTime(takenAt, modifiedAt, null)
                val isImage = item.contentType.startsWith("image/")
                emit(
                    Media(
                        id = 0,
                        uri = buildUrl(item.href.toString()).toUri(),
                        date = sortDate,
                        time = sortTime,
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
                        video = Video(duration = 0).takeIf { !isImage },
                        takenAt = takenAt,
                        modifiedAt = modifiedAt,
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

    private fun stampUrls(root: String): List<String> {
        val urls = ArrayList<String>()
        val pending = ArrayDeque<String>()
        val seen = HashSet<String>()
        pending.add(root)
        while (pending.isNotEmpty()) {
            val url = pending.removeFirst()
            if (!seen.add(url)) continue
            val folderUrlPath = runCatching { java.net.URI(url).path }.getOrNull()
            val key = FolderScanState.folderKey(folderUrlPath, rootPath)
            if (folderScan.savedStamp(key).isNullOrBlank()) continue
            urls += url
            folderScan.savedChildUrls(key).forEach { pending.addLast(it) }
        }
        return urls
    }

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
