package com.asinosoft.gallery.data

import android.net.Uri
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media ORDER BY date DESC, time DESC")
    fun getImages(): Flow<List<Media>>

    @Query("SELECT uri FROM media WHERE id = :mediaId")
    fun getUri(mediaId: Long): Uri

    @Query("SELECT uri FROM media WHERE id IN (:mediaIds)")
    fun getUris(mediaIds: Collection<Long>): List<Uri>

    @Query("SELECT * FROM media WHERE uri=:uri")
    fun getImageByPath(uri: Uri): Flow<Media?>

    @Upsert
    suspend fun upsert(media: Media)

    @Upsert
    suspend fun upsertAll(media: Collection<Media>)

    @Query("DELETE FROM media WHERE id IN (:mediaIds)")
    suspend fun deleteAll(mediaIds: Collection<Long>)
}
