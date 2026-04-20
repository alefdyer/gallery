package com.asinosoft.gallery.data

import android.net.Uri
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaDao {
    @Query("SELECT * FROM media ORDER BY date DESC, time DESC")
    fun getImages(): Flow<List<Media>>

    @Query("SELECT * FROM media WHERE uri = :uri")
    fun getImageByUri(uri: Uri): Media?

    @Query("SELECT * FROM media WHERE uri IN (:uris)")
    fun getImagesByUris(uris: Collection<Uri>): List<Media>

    @Query("SELECT id FROM media WHERE uri IN (:uris)")
    suspend fun getMediaIdsByUris(uris: Collection<Uri>): List<Long>

    @Query("SELECT uri FROM media WHERE id = :mediaId")
    fun getUri(mediaId: Long): Uri

    @Query("SELECT uri FROM media WHERE id IN (:mediaIds)")
    fun getUris(mediaIds: Collection<Long>): List<Uri>

    @Query("SELECT * FROM media WHERE uri=:uri")
    fun getImageByPath(uri: Uri): Flow<Media?>

    @Insert
    suspend fun insert(media: Media): Long

    @Insert
    suspend fun insertAll(media: Collection<Media>): List<Long>

    @Upsert
    suspend fun upsert(media: Media): Long

    @Upsert
    suspend fun upsertAll(media: Collection<Media>): List<Long>

    @Query("DELETE FROM media WHERE id IN (:mediaIds)")
    suspend fun deleteAll(mediaIds: Collection<Long>)

    @Query("DELETE FROM media WHERE id NOT IN (:mediaIds)")
    suspend fun deleteAllExcept(mediaIds: Collection<Long>)
}
