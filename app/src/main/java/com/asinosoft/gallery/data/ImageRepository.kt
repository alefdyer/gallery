package com.asinosoft.gallery.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore.Images.Media
import androidx.compose.ui.unit.IntSize
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import java.time.ZoneId
import java.util.Date
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ImageRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    fun findAll(): List<Image> = with(Dispatchers.IO) {
        val collection = Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            Media._ID,
            Media.DATE_ADDED,
            Media.DATE_TAKEN,
            Media.WIDTH,
            Media.HEIGHT,
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
            val dateAddedColumn = cursor.getColumnIndexOrThrow(Media.DATE_ADDED)
            val dateTakenColumn = cursor.getColumnIndexOrThrow(Media.DATE_TAKEN)
            val widthColumn = cursor.getColumnIndexOrThrow(Media.WIDTH)
            val heightColumn = cursor.getColumnIndexOrThrow(Media.HEIGHT)

            while (cursor.moveToNext()) {
                // Get values of columns for a given video.
                val id = cursor.getLong(idColumn)
                val dateAdded: Long = cursor.getLong(dateAddedColumn)
                val dateTaken: Long = cursor.getLong(dateTakenColumn)
                val date = if (dateTaken > 0) dateTaken else (dateAdded * 1000)
                val width: Int = cursor.getInt(widthColumn)
                val height: Int = cursor.getInt(heightColumn)

                val url = ContentUris.withAppendedId(collection, id)
                val time = Date(date).toInstant().atZone(ZoneId.systemDefault()).toLocalDate()

                val image = Image(
                    url,
                    time,
                    IntSize(width, height),
                )
                images.add(image)
            }
        }

        images
    }
}
