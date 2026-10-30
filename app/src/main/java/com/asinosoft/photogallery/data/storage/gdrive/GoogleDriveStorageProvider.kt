package com.asinosoft.photogallery.data.storage.gdrive

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.core.net.toUri
import com.asinosoft.photogallery.data.Image
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.ThumbnailCache
import com.asinosoft.photogallery.data.Video
import com.asinosoft.photogallery.data.bestVideoDurationMs
import com.asinosoft.photogallery.data.mediaSortDateTime
import com.asinosoft.photogallery.data.parseRfc3339Millis
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.data.storage.StorageCheckResult
import com.asinosoft.photogallery.data.storage.StorageProvider
import com.google.gson.Gson
import java.io.File
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class GoogleDriveStorageProvider(
    override val storage: Storage,
    private val context: Context,
) : StorageProvider {
    private val client = OkHttpClient()
    private val gson = Gson()

    override fun authorize(request: Request): Request {
        val token = storage.password
        if (token.isNullOrBlank()) return request
        return request.newBuilder()
            .header("Authorization", "Bearer $token")
            .build()
    }

    override suspend fun checkConnection(): StorageCheckResult {
        val token = storage.password
        if (token.isNullOrBlank()) return StorageCheckResult.AuthorizationFailed
        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$DRIVE_FILES?pageSize=1&fields=files(id)")
                    .header("Authorization", "Bearer $token")
                    .get()
                    .build()
                client.newCall(request).execute().use { response ->
                    when (response.code) {
                        in 200..299 -> StorageCheckResult.Success
                        401, 403 -> StorageCheckResult.AuthorizationFailed
                        else -> StorageCheckResult.UnknownError("HTTP ${response.code}")
                    }
                }
            } catch (_: UnknownHostException) {
                StorageCheckResult.ServerNotFound
            } catch (_: ConnectException) {
                StorageCheckResult.ServerNotFound
            } catch (_: SocketTimeoutException) {
                StorageCheckResult.ServerNotFound
            } catch (ex: Throwable) {
                StorageCheckResult.UnknownError(ex.message)
            }
        }
    }

    override suspend fun fetchAll(): Flow<Media> {
        val token = storage.password
        if (token.isNullOrBlank()) {
            return flow { error("Google Drive: missing OAuth token") }
        }

        return flow {
            val rootId = withContext(Dispatchers.IO) { fetchRootId(token) }
            // Один проход по папкам вместо GET на каждую — иначе refresh минутами.
            val folderNames = withContext(Dispatchers.IO) { prefetchFolderNames(token) }
            Log.i(TAG, "Drive folders cached: ${folderNames.size}")

            var pageToken: String? = null
            var listed = 0
            do {
                val q =
                    "trashed=false and (mimeType contains 'image/' or mimeType contains 'video/')"
                val url = buildString {
                    append(DRIVE_FILES)
                    append("?pageSize=$LIST_PAGE_SIZE")
                    append("&q=").append(Uri.encode(q))
                    append(
                        "&fields=nextPageToken,files(id,name,mimeType,size,createdTime,modifiedTime," +
                                "thumbnailLink,parents,imageMediaMetadata(time)," +
                                "videoMediaMetadata(durationMillis))"
                    )
                    if (pageToken != null) append("&pageToken=").append(Uri.encode(pageToken))
                }
                val page = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(url)
                        .header("Authorization", "Bearer $token")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            error("Google Drive list failed: HTTP ${response.code}")
                        }
                        response.body?.use { gson.fromJson(it.string(), DriveFileList::class.java) }
                            ?: error("Google Drive list failed: empty body")
                    }
                }

                val files = page.files.orEmpty()
                val missingParents = files.mapNotNull { it.parents?.firstOrNull() }
                    .filter { it != rootId && it !in folderNames }
                    .distinct()
                if (missingParents.isNotEmpty()) {
                    withContext(Dispatchers.IO) {
                        resolveFolderNames(token, missingParents, folderNames)
                    }
                }

                files.forEach { file ->
                    file.toMedia(storage, rootId, folderNames)?.let { emit(it) }
                }
                listed += files.size
                if (listed % 1000 < files.size) {
                    Log.i(TAG, "Drive listed $listed media…")
                }
                pageToken = page.nextPageToken
            } while (!pageToken.isNullOrBlank())
            Log.i(TAG, "Drive list done: $listed media")
        }
    }

    override suspend fun fetchOne(uri: Uri): Media? = null

    override suspend fun getMediaUri(media: Media): Uri =
        // acknowledgeAbuse — иначе часть файлов, помеченных Drive как risky, отдаёт 403.
        "$DRIVE_FILES/${media.storageItemId}?alt=media&acknowledgeAbuse=true".toUri()

    /**
     * thumbnailLink в Drive живёт часы — в БД он быстро протухает.
     * Как у Dropbox: качаем свежую превью в [ThumbnailCache], не отдаём полный alt=media.
     */
    override suspend fun getThumbnailUri(media: Media): Uri = withContext(Dispatchers.IO) {
        if (media.id > 0) {
            val cached = ThumbnailCache.getFile(context, media.id)
            if (cached.exists() && cached.length() > 0) return@withContext cached.toUri()
            if (downloadThumbnail(media, cached)) {
                return@withContext cached.toUri()
            }
        }
        "gdrive-thumb-missing://".toUri()
    }

    private fun downloadThumbnail(media: Media, target: File): Boolean {
        val token = storage.password ?: return false
        val link = fetchThumbnailLink(token, media.storageItemId)
            ?: media.thumbnail?.toString()
            ?: return false
        val url = bumpThumbnailSize(link)
        val request = Request.Builder()
            .url(url)
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Thumb download HTTP ${response.code} ${media.filename}")
                    return false
                }
                val bytes = response.body?.bytes() ?: return false
                if (bytes.isEmpty()) return false
                target.parentFile?.mkdirs()
                target.writeBytes(bytes)
                true
            }
        } catch (ex: Throwable) {
            Log.w(TAG, "Thumb download failed ${media.filename}", ex)
            false
        }
    }

    private fun fetchThumbnailLink(token: String, fileId: String): String? {
        val request = Request.Builder()
            .url("$DRIVE_FILES/$fileId?fields=thumbnailLink")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.use {
                    gson.fromJson(it.string(), DriveFile::class.java)?.thumbnailLink
                }
            }
        } catch (ex: Throwable) {
            Log.w(TAG, "thumbnailLink fetch failed $fileId", ex)
            null
        }
    }

    /** Drive отдаёт =s220; для сетки чуть крупнее. */
    private fun bumpThumbnailSize(link: String): String =
        THUMB_SIZE_REGEX.replace(link, "=s400")

    private fun fetchRootId(token: String): String? {
        val request = Request.Builder()
            .url("$DRIVE_FILES/root?fields=id")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                response.body?.use {
                    gson.fromJson(it.string(), DriveFile::class.java)?.id
                }
            }
        } catch (ex: Throwable) {
            Log.w(TAG, "Can't resolve Drive root id", ex)
            null
        }
    }

    /** Все папки одним list — дешевле, чем files.get на каждый parent. */
    private fun prefetchFolderNames(token: String): MutableMap<String, String> {
        val cache = HashMap<String, String>()
        var pageToken: String? = null
        do {
            val q = "trashed=false and mimeType='application/vnd.google-apps.folder'"
            val url = buildString {
                append(DRIVE_FILES)
                append("?pageSize=$LIST_PAGE_SIZE")
                append("&q=").append(Uri.encode(q))
                append("&fields=nextPageToken,files(id,name)")
                if (pageToken != null) append("&pageToken=").append(Uri.encode(pageToken))
            }
            val request = Request.Builder()
                .url(url)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            val page = try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        Log.w(TAG, "Folder prefetch failed: HTTP ${response.code}")
                        return cache
                    }
                    response.body?.use { gson.fromJson(it.string(), DriveFileList::class.java) }
                        ?: return cache
                }
            } catch (ex: Throwable) {
                Log.w(TAG, "Folder prefetch failed", ex)
                return cache
            }
            for (folder in page.files.orEmpty()) {
                val id = folder.id ?: continue
                val name = folder.name?.takeIf { it.isNotBlank() } ?: id
                cache[id] = name
            }
            pageToken = page.nextPageToken
        } while (!pageToken.isNullOrBlank())
        return cache
    }

    private fun resolveFolderNames(
        token: String,
        folderIds: Collection<String>,
        cache: MutableMap<String, String>,
    ) {
        for (id in folderIds) {
            if (id in cache) continue
            val request = Request.Builder()
                .url("$DRIVE_FILES/$id?fields=id,name,mimeType")
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        cache[id] = id
                        return@use
                    }
                    val file = response.body?.use {
                        gson.fromJson(it.string(), DriveFile::class.java)
                    }
                    val name = file?.name?.takeIf { it.isNotBlank() }
                    cache[id] = name ?: id
                }
            } catch (ex: Throwable) {
                Log.w(TAG, "Can't resolve folder $id", ex)
                cache[id] = id
            }
        }
    }

    companion object {
        private const val TAG = "gdrive"
        private const val LIST_PAGE_SIZE = 1000
        private val THUMB_SIZE_REGEX = Regex("=s\\d+")
        const val DRIVE_READONLY_SCOPE = "https://www.googleapis.com/auth/drive.readonly"
        const val DRIVE_FILES = "https://www.googleapis.com/drive/v3/files"
        const val USERINFO = "https://www.googleapis.com/oauth2/v2/userinfo"

        fun fetchEmail(token: String): String? {
            val client = OkHttpClient()
            val request = Request.Builder()
                .url(USERINFO)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            return try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return null
                    val body = response.body?.string() ?: return null
                    Gson().fromJson(body, UserInfo::class.java)?.email
                }
            } catch (_: Throwable) {
                null
            }
        }
    }
}

private data class DriveFileList(
    val files: List<DriveFile>? = null,
    val nextPageToken: String? = null
)

private data class DriveFile(
    val id: String? = null,
    val name: String? = null,
    val mimeType: String? = null,
    val size: String? = null,
    val createdTime: String? = null,
    val modifiedTime: String? = null,
    val thumbnailLink: String? = null,
    val parents: List<String>? = null,
    val imageMediaMetadata: DriveImageMetadata? = null,
    val videoMediaMetadata: DriveVideoMetadata? = null,
) {
    fun toMedia(
        storage: Storage,
        rootId: String?,
        folderNames: Map<String, String>,
    ): Media? {
        val id = id ?: return null
        val filename = name ?: return null
        val mime = mimeType.orEmpty()
        val isImage = mime.startsWith("image/")
        val isVideo = mime.startsWith("video/")
        if (!isImage && !isVideo) return null
        val modifiedAt = parseRfc3339Millis(modifiedTime)
        val createdAt = parseRfc3339Millis(createdTime)
        val takenAt = parseRfc3339Millis(imageMediaMetadata?.time)
        if (modifiedAt == null && createdAt == null && takenAt == null) return null
        val (sortDate, sortTime) = mediaSortDateTime(takenAt, modifiedAt, createdAt)

        val parentId = parents?.firstOrNull()
        val path = when {
            parentId.isNullOrBlank() || parentId == rootId -> ""
            else -> {
                val folder = folderNames[parentId] ?: parentId
                "$folder/"
            }
        }

        val fileSize = size?.toLongOrNull() ?: -1
        val rawDuration = videoMediaMetadata?.durationMillis?.takeIf { it > 0 } ?: 0L
        // Drive часто врёт durationMillis для .MPG — проверка + оценка по размеру.
        val durationMs = bestVideoDurationMs(fileSize, filename, mime, rawDuration)

        return Media(
            date = sortDate,
            time = sortTime,
            path = path,
            size = fileSize,
            filename = filename,
            mimeType = mime,
            storageId = storage.id,
            storageType = storage.type,
            storageItemId = id,
            thumbnail = thumbnailLink?.toUri(),
            image = Image().takeIf { isImage },
            video = Video(duration = durationMs).takeIf { isVideo },
            takenAt = takenAt,
            createdAt = createdAt,
            modifiedAt = modifiedAt,
        )
    }
}

private data class DriveImageMetadata(
    /** RFC 3339, дата/время съёмки из EXIF. */
    val time: String? = null,
)

private data class DriveVideoMetadata(
    val durationMillis: Long? = null,
)

private data class UserInfo(val email: String? = null)
