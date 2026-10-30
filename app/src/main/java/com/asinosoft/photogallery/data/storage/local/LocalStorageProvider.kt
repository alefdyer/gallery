package com.asinosoft.photogallery.data.storage.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.MediaStore.Files.FileColumns
import android.provider.MediaStore.Images
import android.provider.MediaStore.Video as Videos
import androidx.core.database.getStringOrNull
import com.asinosoft.photogallery.data.Image
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.Video
import com.asinosoft.photogallery.data.mediaSortDateTime
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.data.storage.StorageCheckResult
import com.asinosoft.photogallery.data.storage.StorageProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow

class LocalStorageProvider(
    override val storage: Storage,
    @param:ApplicationContext private val context: Context
) : StorageProvider {
    override val syncsFavorites: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private val filesUri: Uri = MediaStore.Files.getContentUri("external")

    override suspend fun fetchAll(): Flow<Media> = fetchMedia()

    override suspend fun checkConnection() = StorageCheckResult.Success

    override fun getMediaCount(): Int = context.contentResolver.query(
        filesUri,
        arrayOf(FileColumns._ID),
        typeClause(mediaTypes),
        null,
        null
    )?.use { it.count } ?: 0

    override suspend fun fetchOne(uri: Uri): Media? {
        val types = when {
            uri.toString().startsWith(Images.Media.EXTERNAL_CONTENT_URI.toString()) -> imageTypes
            uri.toString().startsWith(Videos.Media.EXTERNAL_CONTENT_URI.toString()) -> videoTypes
            else -> return null
        }
        val id = uri.lastPathSegment?.toLongOrNull()
        val selection = if (id != null) "${FileColumns._ID} = $id" else ""
        return fetchMedia(selection, types).firstOrNull()
    }

    override suspend fun getMediaUri(media: Media): Uri = ContentUris.withAppendedId(
        if (media.image !=
            null
        ) {
            Images.Media.EXTERNAL_CONTENT_URI
        } else {
            Videos.Media.EXTERNAL_CONTENT_URI
        },
        media.storageItemId.toLong()
    )

    override suspend fun getThumbnailUri(media: Media): Uri = getMediaUri(media)

    private fun fetchMedia(selection: String = "", types: IntArray = mediaTypes): Flow<Media> =
        flow {
            val typeSelection = typeClause(types)
            val where = if (selection.isEmpty()) {
                typeSelection
            } else {
                "($selection) AND $typeSelection"
            }
            val projection = arrayOf(
                FileColumns._ID,
                FileColumns.MEDIA_TYPE,
                FileColumns.DATE_ADDED,
                FileColumns.DATE_TAKEN,
                FileColumns.DATE_MODIFIED,
                FileColumns.WIDTH,
                FileColumns.HEIGHT,
                FileColumns.ORIENTATION,
                FileColumns.DURATION,
                FileColumns.SIZE,
                FileColumns.DATA,
                FileColumns.MIME_TYPE
            ) + if (Build.VERSION.SDK_INT >= 28) {
                arrayOf(
                    FileColumns.RELATIVE_PATH,
                    FileColumns.OWNER_PACKAGE_NAME
                )
            } else {
                arrayOf()
            } + if (syncsFavorites) arrayOf(MediaStore.MediaColumns.IS_FAVORITE) else arrayOf()

            context.contentResolver.query(
                filesUri,
                projection,
                where,
                null,
                "${FileColumns.DATE_TAKEN} DESC"
            )?.use { cursor ->
                val zoneId = ZoneId.systemDefault()
                val idColumn = cursor.getColumnIndexOrThrow(FileColumns._ID)
                val mediaTypeColumn = cursor.getColumnIndexOrThrow(FileColumns.MEDIA_TYPE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(FileColumns.DATE_ADDED)
                val dateTakenColumn = cursor.getColumnIndexOrThrow(FileColumns.DATE_TAKEN)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(FileColumns.DATE_MODIFIED)
                val widthColumn = cursor.getColumnIndexOrThrow(FileColumns.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(FileColumns.HEIGHT)
                val orientationColumn = cursor.getColumnIndexOrThrow(FileColumns.ORIENTATION)
                val durationColumn = cursor.getColumnIndexOrThrow(FileColumns.DURATION)
                val sizeColumn = cursor.getColumnIndexOrThrow(FileColumns.SIZE)
                val dataColumn = cursor.getColumnIndexOrThrow(FileColumns.DATA)
                val mimeTypeColumn = cursor.getColumnIndexOrThrow(FileColumns.MIME_TYPE)
                val pathColumn = if (Build.VERSION.SDK_INT < 28) {
                    null
                } else {
                    cursor.getColumnIndexOrThrow(FileColumns.RELATIVE_PATH)
                }
                val ownerColumn = if (Build.VERSION.SDK_INT < 28) {
                    null
                } else {
                    cursor.getColumnIndexOrThrow(FileColumns.OWNER_PACKAGE_NAME)
                }
                val favoriteColumn = if (syncsFavorites) {
                    cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.IS_FAVORITE)
                } else {
                    null
                }

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val isVideo = cursor.getInt(mediaTypeColumn) == FileColumns.MEDIA_TYPE_VIDEO
                    val dateAddedSec: Long = cursor.getLong(dateAddedColumn)
                    val dateTakenMs: Long = cursor.getLong(dateTakenColumn)
                    val dateModifiedSec: Long = cursor.getLong(dateModifiedColumn)
                    val takenAt = dateTakenMs.takeIf { it > 0L }
                    val createdAt = (dateAddedSec * 1000L).takeIf { dateAddedSec > 0L }
                    val modifiedAt = (dateModifiedSec * 1000L).takeIf { dateModifiedSec > 0L }
                    val (sortDate, sortTime) = mediaSortDateTime(
                        takenAt,
                        modifiedAt,
                        createdAt,
                        zoneId,
                    )
                    val collection = if (isVideo) {
                        Videos.Media.EXTERNAL_CONTENT_URI
                    } else {
                        Images.Media.EXTERNAL_CONTENT_URI
                    }
                    val uri = ContentUris.withAppendedId(collection, id)
                    val path: String = pathColumn?.let { cursor.getString(pathColumn) } ?: ""
                    val owner: String? = ownerColumn?.let { cursor.getStringOrNull(ownerColumn) }

                    emit(
                        Media(
                            id = 0,
                            uri = uri,
                            date = sortDate,
                            time = sortTime,
                            path = path,
                            size = cursor.getLong(sizeColumn),
                            filename = cursor.getString(dataColumn),
                            mimeType = cursor.getString(mimeTypeColumn),
                            storageId = storage.id,
                            storageType = storage.type,
                            storageItemId = id.toString(),
                            owner = owner,
                            image = if (isVideo) {
                                null
                            } else {
                                Image(
                                    width = cursor.getInt(widthColumn),
                                    height = cursor.getInt(heightColumn),
                                    orientation = cursor.getInt(orientationColumn)
                                )
                            },
                            video = if (isVideo) Video(cursor.getLong(durationColumn)) else null,
                            favorite = favoriteColumn?.let { 1 == cursor.getInt(it) } ?: false,
                            takenAt = takenAt,
                            createdAt = createdAt,
                            modifiedAt = modifiedAt,
                        )
                    )
                }
            }
        }

    private companion object {
        val imageTypes = intArrayOf(FileColumns.MEDIA_TYPE_IMAGE)
        val videoTypes = intArrayOf(FileColumns.MEDIA_TYPE_VIDEO)
        val mediaTypes = intArrayOf(FileColumns.MEDIA_TYPE_IMAGE, FileColumns.MEDIA_TYPE_VIDEO)

        fun typeClause(types: IntArray): String =
            "${FileColumns.MEDIA_TYPE} IN (${types.joinToString(",")})"
    }
}
