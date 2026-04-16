package com.asinosoft.gallery.data

import android.net.Uri
import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media ORDER BY date DESC, time DESC")
    fun getImages(): Flow<List<Media>>

    @Query("SELECT * FROM media WHERE uri=:uri")
    fun getImageByPath(uri: Uri): Flow<Media?>

    @Upsert
    suspend fun upsert(media: Media)

    @Upsert
    suspend fun upsertAll(media: Collection<Media>)

    @Delete
    suspend fun deleteAll(media: Collection<Media>)
}
