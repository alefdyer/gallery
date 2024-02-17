package com.asinosoft.gallery.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore.Images.Media
import androidx.core.database.getStringOrNull
import kotlinx.coroutines.Dispatchers
import java.time.ZoneId
import java.util.Date

class ImageRepository(private val context: Context) {
    fun findAll(): List<Image> = with(Dispatchers.IO) {
        val collection = Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            Media._ID,
            Media.DISPLAY_NAME,
            Media.DATE_ADDED,
            Media.DATE_TAKEN,
            Media.WIDTH,
            Media.HEIGHT,
            Media.RESOLUTION,
            Media.OWNER_PACKAGE_NAME,
            Media.VOLUME_NAME,
        )

        val sortOrder = "${Media.DATE_TAKEN} DESC"

        val query = context.contentResolver.query(
            collection,
            projection,
            "",
            arrayOf(),
            sortOrder
        )

        val images = ArrayList<Image>()
        query?.use { cursor ->
            // Cache column indices.
            val idColumn = cursor.getColumnIndexOrThrow(Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(Media.DISPLAY_NAME)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(Media.DATE_ADDED)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(Media.DATE_TAKEN)
            val widthColumn = cursor.getColumnIndexOrThrow(Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(Media.HEIGHT)
            val resolutionColumn = cursor.getColumnIndexOrThrow(Media.RESOLUTION)
            val ownerColumn =
                cursor.getColumnIndexOrThrow(Media.OWNER_PACKAGE_NAME)
            val volumeColumn = cursor.getColumnIndexOrThrow(Media.VOLUME_NAME)

            while (cursor.moveToNext()) {
                // Get values of columns for a given video.
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn)
                val dateAdded: Long = cursor.getLong(dateAddedColumn)
                val dateTaken: Long = cursor.getLong(dateTakenColumn)
                val date = if (dateTaken > 0) dateTaken else (dateAdded * 1000)
                val width: Int = cursor.getInt(widthColumn)
                val height: Int = cursor.getInt(heightColumn)
                val resolution: String? = cursor.getStringOrNull(resolutionColumn)
                val owner: String? = cursor.getStringOrNull(ownerColumn)
                val volume: String? = cursor.getStringOrNull(volumeColumn)

                val url = ContentUris.withAppendedId(collection, id)
                val time = Date(date).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

                val image = Image(
                    url,
                    time,
                    width,
                    height,
                    resolution,
                    owner,
                    volume,
                )
                images.add(image)
            }
        }

        images
    }
}
