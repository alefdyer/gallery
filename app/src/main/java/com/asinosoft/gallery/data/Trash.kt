package com.asinosoft.gallery.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.database.getStringOrNull
import com.asinosoft.gallery.GalleryApp
import com.asinosoft.gallery.data.storage.StorageType
import com.asinosoft.gallery.di.IntentHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Системная корзина MediaStore (Android 11+): удалённые файлы хранятся 30 дней,
 * после чего система удаляет их сама. Корзина общая для всех галерей на телефоне.
 */
@Singleton
class Trash @Inject constructor(
    private val analytics: AppAnalytics,
    @param:ApplicationContext private val context: Context
) {
    val isSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    private val version = MutableStateFlow(0)

    val media: Flow<List<Media>> = version
        .map { if (isSupported) query() else emptyList() }
        .flowOn(Dispatchers.IO)

    fun refresh() = version.update { it + 1 }

    private val _trashed = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /** Количество файлов, только что перенесённых в корзину */
    val trashed: SharedFlow<Int> = _trashed

    fun onTrashed(count: Int) {
        refresh()
        _trashed.tryEmit(count)
    }

    fun restore(items: Collection<Media>) {
        val uris = items.mapNotNull { it.uri }
        if (uris.isEmpty()) return
        IntentHelper.trashRequest(uris, context, trashed = false) { refresh() }
    }

    fun delete(items: Collection<Media>) {
        val uris = items.mapNotNull { it.uri }
        if (uris.isEmpty()) return
        IntentHelper.deleteRequest(uris, context) { granted ->
            if (granted) analytics.mediaDeleted(AppAnalytics.SOURCE_TRASH)
            refresh()
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun query(): List<Media> {
        val args = Bundle().apply {
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
            putString(
                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
            )
            putStringArray(
                android.content.ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString()
                )
            )
        }
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.RELATIVE_PATH,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.ORIENTATION,
        )

        val result = mutableListOf<Media>()
        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                projection,
                args,
                null
            )?.use { cursor ->
                val zoneId = ZoneId.systemDefault()
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val typeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val dateTakenColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val mimeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val pathColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
                val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val orientationColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.ORIENTATION)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val video = MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO == cursor.getInt(typeColumn)
                    val dateTaken = cursor.getLong(dateTakenColumn)
                    val millis = if (dateTaken > 0) dateTaken else cursor.getLong(dateAddedColumn) * 1000
                    val datetime = Instant.ofEpochMilli(millis).atZone(zoneId).toLocalDateTime()
                    val collection = if (video) {
                        MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    } else {
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                    }
                    result += Media(
                        id = TRASH_ID_OFFSET - id,
                        uri = ContentUris.withAppendedId(collection, id),
                        date = datetime.toLocalDate(),
                        time = datetime.toLocalTime(),
                        path = cursor.getStringOrNull(pathColumn).orEmpty(),
                        size = cursor.getLong(sizeColumn),
                        filename = cursor.getStringOrNull(dataColumn).orEmpty(),
                        mimeType = cursor.getStringOrNull(mimeColumn).orEmpty(),
                        storageId = EXTERNAL_STORAGE_ID,
                        storageType = StorageType.LOCAL,
                        storageItemId = id.toString(),
                        image = if (video) null else Image(
                            cursor.getInt(widthColumn),
                            cursor.getInt(heightColumn),
                            cursor.getInt(orientationColumn)
                        ),
                        video = if (video) Video(cursor.getLong(durationColumn)) else null
                    )
                }
            }
        } catch (ex: Exception) {
            Log.e(GalleryApp.TAG, "Can't read trash", ex)
        }
        return result.sortedWith(compareByDescending<Media> { it.date }.thenByDescending { it.time })
    }

    private companion object {
        /** Отрицательные id, чтобы не пересекаться с id из базы и личной папки */
        const val TRASH_ID_OFFSET = -(1L shl 40)
    }
}
