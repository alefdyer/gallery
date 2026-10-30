package com.asinosoft.photogallery.data.storage.onedrive

import android.net.Uri
import androidx.core.net.toUri
import com.asinosoft.photogallery.data.Image
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.Video
import com.asinosoft.photogallery.data.mediaSortDateTime
import com.asinosoft.photogallery.data.parseRfc3339Millis
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.data.storage.StorageCheckResult
import com.asinosoft.photogallery.data.storage.StorageProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class OneDriveStorageProvider(override val storage: Storage) : StorageProvider {
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
                    .url("${OneDriveApi.GRAPH_BASE}/me/drive")
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
        if (token.isNullOrBlank()) return flow { }

        return flow {
            var url: String? =
                "${OneDriveApi.GRAPH_BASE}/me/drive/root/delta" +
                        "?\$select=id,name,size,createdDateTime,lastModifiedDateTime," +
                        "file,folder,parentReference,photo" +
                        "&\$expand=thumbnails"
            while (url != null) {
                val page = withContext(Dispatchers.IO) {
                    val request = Request.Builder()
                        .url(url!!)
                        .header("Authorization", "Bearer $token")
                        .get()
                        .build()
                    client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) return@withContext null
                        response.body?.use { gson.fromJson(it.string(), DeltaPage::class.java) }
                    }
                } ?: break

                page.value.orEmpty().forEach { item ->
                    item.toMedia(storage)?.let { emit(it) }
                }
                url = page.nextLink
            }
        }
    }

    override suspend fun fetchOne(uri: Uri): Media? = null

    override suspend fun getMediaUri(media: Media): Uri = withContext(Dispatchers.IO) {
        val token = storage.password
            ?: throw IllegalStateException("OneDrive token missing")
        val request = Request.Builder()
            .url("${OneDriveApi.GRAPH_BASE}/me/drive/items/${media.storageItemId}?\$select=id,@microsoft.graph.downloadUrl")
            .header("Authorization", "Bearer $token")
            .get()
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IllegalStateException("OneDrive download URL HTTP ${response.code}")
            }
            val item = response.body?.use { gson.fromJson(it.string(), DriveItem::class.java) }
            item?.downloadUrl?.toUri()
                ?: "${OneDriveApi.GRAPH_BASE}/me/drive/items/${media.storageItemId}/content".toUri()
        }
    }

    override suspend fun getThumbnailUri(media: Media): Uri =
        media.thumbnail
            ?: "${OneDriveApi.GRAPH_BASE}/me/drive/items/${media.storageItemId}/thumbnails/0/medium/content".toUri()
}

private data class DeltaPage(
    val value: List<DriveItem>? = null,
    @SerializedName("@odata.nextLink")
    val nextLink: String? = null
)

private data class DriveItem(
    val id: String? = null,
    val name: String? = null,
    val size: Long? = null,
    val createdDateTime: String? = null,
    val lastModifiedDateTime: String? = null,
    val file: DriveFileFacet? = null,
    val folder: Any? = null,
    val photo: PhotoFacet? = null,
    val parentReference: ParentRef? = null,
    val thumbnails: List<ThumbnailSet>? = null,
    @SerializedName("@microsoft.graph.downloadUrl")
    val downloadUrl: String? = null
) {
    fun toMedia(storage: Storage): Media? {
        if (folder != null || file == null) return null
        val id = id ?: return null
        val filename = name ?: return null
        val mime = file.mimeType.orEmpty()
        val isImage = mime.startsWith("image/")
        val isVideo = mime.startsWith("video/")
        if (!isImage && !isVideo) return null
        val modifiedAt = parseRfc3339Millis(lastModifiedDateTime)
        val createdAt = parseRfc3339Millis(createdDateTime)
        val takenAt = parseRfc3339Millis(photo?.takenDateTime)
        if (modifiedAt == null && createdAt == null && takenAt == null) return null
        val (sortDate, sortTime) = mediaSortDateTime(takenAt, modifiedAt, createdAt)
        val thumb = thumbnails?.firstOrNull()?.medium?.url?.toUri()
            ?: thumbnails?.firstOrNull()?.large?.url?.toUri()
        return Media(
            date = sortDate,
            time = sortTime,
            path = parentReference?.path.orEmpty(),
            size = size ?: -1,
            filename = filename,
            mimeType = mime,
            storageId = storage.id,
            storageType = storage.type,
            storageItemId = id,
            thumbnail = thumb,
            image = Image().takeIf { isImage },
            video = Video(duration = 0).takeIf { isVideo },
            takenAt = takenAt,
            createdAt = createdAt,
            modifiedAt = modifiedAt,
        )
    }
}

private data class DriveFileFacet(val mimeType: String? = null)
private data class PhotoFacet(val takenDateTime: String? = null)
private data class ParentRef(val path: String? = null, val id: String? = null)
private data class ThumbnailSet(
    val medium: ThumbUrl? = null,
    val large: ThumbUrl? = null
)

private data class ThumbUrl(val url: String? = null)
