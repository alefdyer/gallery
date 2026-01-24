package com.asinosoft.gallery.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore.Images.Media
import androidx.core.database.getStringOrNull
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.util.Date
import java.util.UUID
import javax.inject.Inject

class LocalImageRepository
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) : ImageRepository {
        private companion object {
            private val COLLECTION = Media.EXTERNAL_CONTENT_URI
            private val PROJECTION =
                arrayOf(
                    Media._ID,
                    Media.DATE_ADDED,
                    Media.DATE_TAKEN,
                    Media.WIDTH,
                    Media.HEIGHT,
                    Media.ORIENTATION,
                    Media.BUCKET_DISPLAY_NAME,
                    Media.SIZE,
                    Media.DATA,
                )
        }

        override fun fetchAll(): List<Image> = fetch("")

        override fun fetchOne(path: String): Image = fetch("${Media._ID} = $path").first()

        private fun fetch(selection: String): List<Image> {
            val sortOrder = "${Media.DATE_TAKEN} DESC"

            val query =
                context.contentResolver.query(
                    COLLECTION,
                    PROJECTION,
                    selection,
                    arrayOf(),
                    sortOrder,
                )

            val images = ArrayList<Image>()
            query?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(Media._ID)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(Media.DATE_ADDED)
                val dateTakenColumn = cursor.getColumnIndexOrThrow(Media.DATE_TAKEN)
                val widthColumn = cursor.getColumnIndexOrThrow(Media.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(Media.HEIGHT)
                val orientationColumn = cursor.getColumnIndexOrThrow(Media.ORIENTATION)
                val bucketNameColumn = cursor.getColumnIndexOrThrow(Media.BUCKET_DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(Media.SIZE)
                val dataColumn = cursor.getColumnIndexOrThrow(Media.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val dateAdded: Long = cursor.getLong(dateAddedColumn)
                    val dateTaken: Long = cursor.getLong(dateTakenColumn)
                    val date = if (dateTaken > 0) dateTaken else (dateAdded * 1000)
                    val width: Int = cursor.getInt(widthColumn)
                    val height: Int = cursor.getInt(heightColumn)
                    val orientation: Int = cursor.getInt(orientationColumn)

                    val url = ContentUris.withAppendedId(COLLECTION, id).toString()
                    val datetime =
                        Date(date).toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()

                    val album: String? = cursor.getStringOrNull(bucketNameColumn)
                    val size: Long = cursor.getLong(sizeColumn)

                    val data: String = cursor.getString(dataColumn)

                    val image =
                        Image(
                            id = UUID.randomUUID(),
                            url,
                            datetime.toLocalDate(),
                            datetime.toLocalTime(),
                            width,
                            height,
                            orientation,
                            album,
                            size,
                            filename = data,
                        )
                    images.add(image)
                }
            }

            return images
        }
    }
