package com.asinosoft.gallery.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore.Images
import androidx.core.database.getStringOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class LocalMediaRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : MediaRepository {
        private companion object {
            private val COLLECTION = Images.Media.EXTERNAL_CONTENT_URI
            private val PROJECTION =
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
                )
        }

        override fun fetchAll(): List<Media> = fetch("")

        override fun fetchOne(path: String): Media = fetch("${Images.Media._ID} = $path").first()

        private fun fetch(selection: String): List<Media> {
            val sortOrder = "${Images.Media.DATE_TAKEN} DESC"

            val query =
                context.contentResolver.query(
                    COLLECTION,
                    PROJECTION,
                    selection,
                    arrayOf(),
                    sortOrder,
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

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val dateAdded: Long = cursor.getLong(dateAddedColumn)
                    val dateTaken: Long = cursor.getLong(dateTakenColumn)
                    val date = if (dateTaken > 0) dateTaken else (dateAdded * 1000)
                    val width: Int = cursor.getInt(widthColumn)
                    val height: Int = cursor.getInt(heightColumn)
                    val orientation: Int = cursor.getInt(orientationColumn)

                    val uri = ContentUris.withAppendedId(COLLECTION, id)
                    val datetime =
                        Date(date).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()

                    val album: String? = cursor.getStringOrNull(bucketNameColumn)
                    val size: Long = cursor.getLong(sizeColumn)

                    val data: String = cursor.getString(dataColumn)

                    val image =
                        Media(
                            id = UUID.randomUUID(),
                            uri = uri,
                            date = datetime.toLocalDate(),
                            time = datetime.toLocalTime(),
                            album = album,
                            size = size,
                            filename = data,
                            image =
                                Image(
                                    width = width,
                                    height = height,
                                    orientation,
                                ),
                        )
                    images.add(image)
                }
            }

            return images
        }
    }
