package com.asinosoft.gallery.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore.Images
import android.provider.MediaStore.Video as Videos
import androidx.core.database.getStringOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject

class LocalMediaRepository
@Inject constructor(
    @param:ApplicationContext private val context: Context
) : MediaRepository {
    override fun fetchAll(): List<Media> = fetchImages("") + fetchVideos("")

    override fun fetchOne(uri: Uri): Media =
        if (Images.Media.EXTERNAL_CONTENT_URI.equals(uri.authority)) {
            fetchImages("${Images.Media._ID} = ${uri.lastPathSegment}").first()
        } else {
            fetchVideos("${Videos.Media._ID} = ${uri.lastPathSegment}").first()
        }

    private fun fetchImages(selection: String): List<Media> {
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
                    Images.Media.BUCKET_DISPLAY_NAME,
                    Images.Media.SIZE,
                    Images.Media.DATA,
                    Images.Media.MIME_TYPE
                ),
                selection,
                arrayOf(),
                sortOrder
            )

        val images = ArrayList<Media>()
        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Images.Media._ID)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(Images.Media.DATE_ADDED)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(Images.Media.DATE_TAKEN)
            val widthColumn = cursor.getColumnIndexOrThrow(Images.Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(Images.Media.HEIGHT)
            val orientationColumn = cursor.getColumnIndexOrThrow(Images.Media.ORIENTATION)
            val bucketNameColumn = cursor.getColumnIndexOrThrow(Images.Media.BUCKET_DISPLAY_NAME)
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

                val bucket: String? = cursor.getStringOrNull(bucketNameColumn)
                val size: Long = cursor.getLong(sizeColumn)

                val data: String = cursor.getString(dataColumn)
                val mimeType: String = cursor.getString(mimeTypeColumn)

                val image =
                    Media(
                        id = 0,
                        uri = uri,
                        date = datetime.toLocalDate(),
                        time = datetime.toLocalTime(),
                        bucket = bucket,
                        size = size,
                        filename = data,
                        mimeType = mimeType,
                        image =
                            Image(
                                width = width,
                                height = height,
                                orientation
                            )
                    )
                images.add(image)
            }
        }

        return images
    }

    private fun fetchVideos(selection: String): List<Media> {
        val cursor =
            context.contentResolver.query(
                Videos.Media.EXTERNAL_CONTENT_URI,
                arrayOf(
                    Videos.Media._ID,
                    Videos.Media.DISPLAY_NAME,
                    Videos.Media.DURATION,
                    Videos.Media.BUCKET_DISPLAY_NAME,
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

        val videos = ArrayList<Media>()
        cursor?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Videos.Media._ID)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(Videos.Media.DATE_ADDED)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(Videos.Media.DATE_TAKEN)
            val bucketNameColumn =
                cursor.getColumnIndexOrThrow(Videos.Media.BUCKET_DISPLAY_NAME)
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

                val bucket: String? = cursor.getStringOrNull(bucketNameColumn)
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
                        bucket = bucket,
                        size = size,
                        filename = data,
                        mimeType = mimeType,
                        video = Video(duration)
                    )

                videos.add(video)
            }
        }

        return videos
    }
}
