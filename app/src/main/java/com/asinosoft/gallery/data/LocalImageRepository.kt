package com.asinosoft.gallery.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore.Images.Media
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject

class LocalImageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) : ImageRepository {
    private companion object {
        private val COLLECTION = Media.EXTERNAL_CONTENT_URI
        private val PROJECTION = arrayOf(
            Media._ID,
            Media.DATE_ADDED,
            Media.DATE_TAKEN,
            Media.WIDTH,
            Media.HEIGHT,
            Media.OWNER_PACKAGE_NAME,
            Media.VOLUME_NAME,
        )
    }

    override fun fetchAll(): List<Image> =
        fetch("")

    override fun fetchOne(path: String): Image =
        fetch("${Media._ID} = $path").first()

    private fun fetch(selection: String): List<Image> {
        val sortOrder = "${Media.DATE_TAKEN} DESC"

        val query = context.contentResolver.query(
            COLLECTION,
            PROJECTION,
            selection,
            arrayOf(),
            sortOrder
        )

        val images = ArrayList<Image>()
        query?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(Media._ID)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(Media.DATE_ADDED)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(Media.DATE_TAKEN)
            val widthColumn = cursor.getColumnIndexOrThrow(Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(Media.HEIGHT)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val dateAdded: Long = cursor.getLong(dateAddedColumn)
                val dateTaken: Long = cursor.getLong(dateTakenColumn)
                val date = if (dateTaken > 0) dateTaken else (dateAdded * 1000)
                val width: Int = cursor.getInt(widthColumn)
                val height: Int = cursor.getInt(heightColumn)

                val url = ContentUris.withAppendedId(COLLECTION, id).toString()
                val time = Date(date).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

                val image = Image(
                    url,
                    time,
                    width,
                    height,
                )
                images.add(image)
            }
        }

        return images
    }
}
