package com.asinosoft.photogallery.data.storage.yandex

import android.net.Uri
import androidx.core.net.toUri
import com.asinosoft.photogallery.BuildConfig
import com.asinosoft.photogallery.data.Image
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.Video
import com.asinosoft.photogallery.data.mediaParentPath
import com.asinosoft.photogallery.data.mediaSortDateTime
import com.asinosoft.photogallery.data.storage.FolderSnapshot
import com.asinosoft.photogallery.data.storage.FolderListingResult
import com.asinosoft.photogallery.data.storage.FolderListingScan
import com.asinosoft.photogallery.data.storage.FolderScanState
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.data.storage.StorageCheckResult
import com.asinosoft.photogallery.data.storage.StorageProvider
import java.io.IOException
import com.asinosoft.photogallery.data.toEpochMillis
import com.google.gson.Gson
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URLEncoder
import java.net.UnknownHostException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

class YandexStorageProvider(override val storage: Storage) : StorageProvider, FolderListingScan {
    private val folderScan = FolderScanState()

    override fun setKnownFolders(folders: List<FolderSnapshot>) {
        folderScan.begin(folders)
    }

    override fun takeFolderListing(): FolderListingResult? = folderScan.take()
    companion object {
        const val BASE_URL = "https://cloud-api.yandex.net/v1/disk/"

        val AUTHORIZATION_URL: String
            get() = "https://oauth.yandex.ru/authorize" +
                "?response_type=token" +
                    "&client_id=${BuildConfig.YANDEX_APP_KEY}" +
                    "&redirect_uri=${Uri.encode(BuildConfig.YANDEX_REDIRECT_URI)}"
    }

    override fun authorize(request: Request): Request = request.newBuilder()
        .header("Authorization", "OAuth ${storage.password}")
        .build()

    override suspend fun checkConnection(): StorageCheckResult {
        val token = storage.password
        if (token.isNullOrBlank()) return StorageCheckResult.AuthorizationFailed

        return withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url(BASE_URL)
                    .header("Authorization", "OAuth $token")
                    .build()
                OkHttpClient().newCall(request).execute().use { response ->
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

    override suspend fun fetchAll(): Flow<Media> = flow {
        val all = ArrayList<Resource>()
        var offset = 0
        while (true) {
            val request = Request.Builder()
                .url(BASE_URL + "resources/files?limit=100&preview_size=M&offset=$offset")
                .header("Authorization", "OAuth ${storage.password}")
                .build()

            val resources = withContext(Dispatchers.IO) {
                OkHttpClient().newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Yandex list HTTP ${response.code}")
                    }
                    response.body?.use {
                        Gson().fromJson(it.string(), ResourceList::class.java)
                    }
                }
            } ?: break

            if (resources.items.isEmpty()) {
                break
            }
            all += resources.items
            offset += resources.items.size
        }

        val byFolder = all.groupBy { mediaParentPath(it.path, "disk:/") }
        for ((folder, items) in byFolder) {
            val parts = items.map { item ->
                "f\t${item.name}\t${item.size}\t${item.modified?.time ?: 0L}\t${item.created.time}"
            }
            if (folderScan.onFolder(folder, parts)) continue
            items
                .filter { it.mediaType == "image" || it.mediaType == "video" }
                .forEach { item ->
                    val takenAt = item.exif?.datetime.toEpochMillis()
                    val modifiedAt = item.modified.toEpochMillis()
                    val createdAt = item.created.toEpochMillis()
                    val (sortDate, sortTime) = mediaSortDateTime(takenAt, modifiedAt, createdAt)
                    val isVideo = item.mediaType == "video"
                    val preview = item.sizes
                        .orEmpty()
                        .firstOrNull { it.name == "M" }
                        ?.url
                        ?.toUri()

                    emit(
                        Media(
                            date = sortDate,
                            time = sortTime,
                            path = folder,
                            size = item.size,
                            filename = item.name,
                            mimeType = item.mimeType,
                            storageId = storage.id,
                            storageType = storage.type,
                            storageItemId = item.path,
                            thumbnail = preview,
                            takenAt = takenAt,
                            createdAt = createdAt,
                            modifiedAt = modifiedAt,
                            image = Image().takeIf { !isVideo },
                            video = Video(duration = 0).takeIf { isVideo }
                        )
                    )
                }
        }
    }

    override suspend fun fetchOne(uri: Uri): Media? {
        TODO("Not yet implemented")
    }

    override suspend fun getMediaUri(media: Media): Uri = withContext(Dispatchers.IO) {
        val path = URLEncoder.encode(media.storageItemId, Charsets.UTF_8.name())
        val request = Request.Builder()
            .url(BASE_URL + "resources/download?path=$path")
            .header("Authorization", "OAuth ${storage.password}")
            .build()

        OkHttpClient().newCall(request).execute().use { response ->
            val resource: Download = response.body!!.use { body ->
                Gson().fromJson(body.string(), Download::class.java)
            }

            resource.href.toUri()
        }
    }

    override suspend fun getThumbnailUri(media: Media): Uri =
        media.thumbnail ?: getMediaUri(media)
}
