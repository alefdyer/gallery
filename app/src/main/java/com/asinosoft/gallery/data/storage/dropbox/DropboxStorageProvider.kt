package com.asinosoft.gallery.data.storage.dropbox

import android.net.Uri
import androidx.core.net.toUri
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.Video
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageCheckResult
import com.asinosoft.gallery.data.storage.StorageProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLConnection
import java.net.UnknownHostException
import java.time.OffsetDateTime
import java.time.ZoneId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class DropboxStorageProvider(override val storage: Storage) : StorageProvider {
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
                val response = apiPost("check/user")
                if (response != null) {
                    StorageCheckResult.Success
                } else {
                    StorageCheckResult.AuthorizationFailed
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
        if (token.isNullOrBlank()) return flow { }

        return flow {
            var hasMore = true
            var cursor: String? = null

            while (hasMore) {
                val requestPayload = if (cursor == null) {
                    gson.toJson(
                        DropboxListFolderRequest(
                            path = "",
                            recursive = true,
                            includeDeleted = false,
                            includeMediaInfo = true,
                            limit = 2000
                        )
                    )
                } else {
                    gson.toJson(DropboxListFolderContinueRequest(cursor = cursor))
                }

                val endpoint = if (cursor == null) {
                    "files/list_folder"
                } else {
                    "files/list_folder/continue"
                }

                val payload = withContext(Dispatchers.IO) {
                    apiPost(endpoint, requestPayload) ?: return@withContext null
                } ?: break

                val page = gson.fromJson(payload, DropboxListFolderResponse::class.java) ?: break
                page.entries.asSequence()
                    .filter { it.tag == "file" }
                    .mapNotNull { item -> item.toMedia(storage) }
                    .forEach { emit(it) }

                cursor = page.cursor
                hasMore = page.hasMore
            }
        }
    }

    override suspend fun fetchOne(uri: Uri): Media? = null

    override suspend fun getMediaUri(media: Media): Uri = withContext(Dispatchers.IO) {
        val payload = gson.toJson(DropboxPathRequest(path = media.storageItemId))
        val response = apiPost("files/get_temporary_link", payload)
            ?: throw IllegalStateException("Failed to resolve media uri")
        val link = gson.fromJson(response, DropboxTemporaryLinkResponse::class.java)?.link
            ?: throw IllegalStateException("Dropbox returned empty temporary link")
        link.toUri()
    }

    override suspend fun getThumbnailUri(media: Media): Uri = getMediaUri(media)

    private fun apiPost(path: String, payload: String = "{}"): String? {
        val request = Request.Builder()
            .url(DropboxApi.BASE_URL + path)
            .post(payload.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer ${storage.password}")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                return null
            }
            return response.body?.string()
        }
    }
}

private data class DropboxListFolderRequest(
    val path: String,
    val recursive: Boolean,
    @SerializedName("include_deleted")
    val includeDeleted: Boolean,
    @SerializedName("include_media_info")
    val includeMediaInfo: Boolean,
    val limit: Int
)

private data class DropboxListFolderContinueRequest(val cursor: String)

private data class DropboxPathRequest(val path: String)

private data class DropboxListFolderResponse(
    val entries: List<DropboxListEntry>,
    val cursor: String?,
    @SerializedName("has_more")
    val hasMore: Boolean
)

private data class DropboxListEntry(
    @SerializedName(".tag")
    val tag: String,
    val id: String?,
    val name: String?,
    @SerializedName("path_display")
    val pathDisplay: String?,
    @SerializedName("server_modified")
    val serverModified: String?,
    val size: Long?
) {
    fun toMedia(storage: Storage): Media? {
        val itemId = id ?: return null
        val filename = name ?: return null
        val mimeType = URLConnection.guessContentTypeFromName(filename).orEmpty()
        val isImage = mimeType.startsWith("image/")
        val isVideo = mimeType.startsWith("video/")
        if (!isImage && !isVideo) return null

        val modified = runCatching {
            OffsetDateTime.parse(serverModified).atZoneSameInstant(ZoneId.systemDefault())
        }.getOrNull() ?: return null

        val parentPath = pathDisplay?.substringBeforeLast('/', "") ?: ""

        return Media(
            date = modified.toLocalDate(),
            time = modified.toLocalTime(),
            path = parentPath,
            size = size ?: -1,
            filename = filename,
            mimeType = mimeType,
            storageId = storage.id,
            storageType = storage.type,
            storageItemId = itemId,
            image = if (isImage) Image() else null,
            video = if (isVideo) Video() else null
        )
    }
}

private data class DropboxTemporaryLinkResponse(val link: String?)
