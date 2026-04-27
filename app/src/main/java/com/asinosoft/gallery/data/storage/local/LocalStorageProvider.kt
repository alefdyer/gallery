package com.asinosoft.gallery.data.storage.local

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore.Images
import android.provider.MediaStore.Video as Videos
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.Video
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageCheckResult
import com.asinosoft.gallery.data.storage.StorageProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.util.Date
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow

class LocalStorageProvider(
    override val storage: Storage,
    @param:ApplicationContext private val context: Context
) : StorageProvider {
    override suspend fun fetchAll(): Flow<Media> = flow {
        emitAll(fetchImages(""))
        emitAll(fetchVideos(""))
    }

    override suspend fun checkConnection() = StorageCheckResult.Success

    override suspend fun fetchOne(uri: Uri): Media? =
        if (uri.toString().startsWith(Images.Media.EXTERNAL_CONTENT_URI.toString())) {
            fetchImages("${Images.Media._ID} = ${uri.lastPathSegment}").first()
        } else if (uri.toString().startsWith(Videos.Media.EXTERNAL_CONTENT_URI.toString())) {
            fetchVideos("${Videos.Media._ID} = ${uri.lastPathSegment}").first()
        } else {
            null
        }

    private fun fetchImages(selection: String): Flow<Media> = flow {
        val sortOrder = "${Images.Media.DATE_TAKEN} DESC"

        val query =
            context.contentResolver.query(
                Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    Images.Media._ID,
                    Images.Media.DATE_ADDED,
                    Images.Media.DATE_TAKEN,
                    Images.Media.WIDTH,
                    Images.Media.HEIGHT,
                    Images.Media.ORIENTATION,
                    Images.Media.RELATIVE_PATH,
                    Images.Media.SIZE,
                    Images.Media.DATA,
                    Images.Media.MIME_TYPE
                ),
                selection,
                arrayOf(),
                sortOrder
            )

        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Images.Media._ID)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(Images.Media.DATE_ADDED)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(Images.Media.DATE_TAKEN)
            val widthColumn = cursor.getColumnIndexOrThrow(Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(Images.Media.HEIGHT)
            val orientationColumn = cursor.getColumnIndexOrThrow(Images.Media.ORIENTATION)
            val pathColumn = cursor.getColumnIndexOrThrow(Images.Media.RELATIVE_PATH)
            val sizeColumn = cursor.getColumnIndexOrThrow(Images.Media.SIZE)
            val dataColumn = cursor.getColumnIndexOrThrow(Images.Media.DATA)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(Images.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAdded: Long = cursor.getLong(dateAddedColumn)
                val dateTaken: Long = cursor.getLong(dateTakenColumn)
                val date = if (dateTaken > 0) dateTaken else (dateAdded * 1000)
                val width: Int = cursor.getInt(widthColumn)
                val height: Int = cursor.getInt(heightColumn)
                val orientation: Int = cursor.getInt(orientationColumn)

                val uri = ContentUris.withAppendedId(Images.Media.EXTERNAL_CONTENT_URI, id)
                val datetime =
                    Date(date).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()

                val path: String = cursor.getString(pathColumn)
                val size: Long = cursor.getLong(sizeColumn)

                val data: String = cursor.getString(dataColumn)
                val mimeType: String = cursor.getString(mimeTypeColumn)

                val image =
                    Media(
                        id = 0,
                        uri = uri,
                        date = datetime.toLocalDate(),
                        time = datetime.toLocalTime(),
                        path = path,
                        size = size,
                        filename = data,
                        mimeType = mimeType,
                        storageId = storage.id,
                        storageItemId = id.toString(),
                        image =
                            Image(
                                width = width,
                                height = height,
                                orientation
                            )
                    )
                emit(image)
            }
        }
    }

    private fun fetchVideos(selection: String): Flow<Media> = flow {
        val cursor =
            context.contentResolver.query(
                Videos.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    Videos.Media._ID,
                    Videos.Media.DISPLAY_NAME,
                    Videos.Media.DURATION,
                    Videos.Media.RELATIVE_PATH,
                    Videos.Media.SIZE,
                    Videos.Media.DATA,
                    Videos.Media.DATE_TAKEN,
                    Videos.Media.DATE_ADDED,
                    Videos.Media.MIME_TYPE
                ),
                selection,
                null,
                "${Videos.Media.DATE_TAKEN} DESC"
            )

        cursor?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Videos.Media._ID)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(Videos.Media.DATE_ADDED)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(Videos.Media.DATE_TAKEN)
            val pathColumn =
                cursor.getColumnIndexOrThrow(Videos.Media.RELATIVE_PATH)
            val sizeColumn = cursor.getColumnIndexOrThrow(Videos.Media.SIZE)
            val dataColumn = cursor.getColumnIndexOrThrow(Videos.Media.DATA)
            val durationColumn = cursor.getColumnIndexOrThrow(Videos.Media.DURATION)
            val mimeTypeColumn = cursor.getColumnIndexOrThrow(Videos.Media.MIME_TYPE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAdded: Long = cursor.getLong(dateAddedColumn)
                val dateTaken: Long = cursor.getLong(dateTakenColumn)
                val date = if (dateTaken > 0) dateTaken else (dateAdded * 1000)

                val uri = ContentUris.withAppendedId(Videos.Media.EXTERNAL_CONTENT_URI, id)
                val datetime =
                    Date(date).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()

                val path: String = cursor.getString(pathColumn)
                val size: Long = cursor.getLong(sizeColumn)

                val data: String = cursor.getString(dataColumn)
                val duration: Long = cursor.getLong(durationColumn)
                val mimeType: String = cursor.getString(mimeTypeColumn)

                val video =
                    Media(
                        id = 0,
                        uri = uri,
                        date = datetime.toLocalDate(),
                        time = datetime.toLocalTime(),
                        path = path,
                        size = size,
                        filename = data,
                        mimeType = mimeType,
                        storageId = storage.id,
                        storageItemId = id.toString(),
                        video = Video(duration)
                    )

                emit(video)
            }
        }
    }
}
