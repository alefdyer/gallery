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

    @Query("SELECT * FROM media WHERE storageId=:storageId AND storageItemId IN (:storageItemIds)")
    suspend fun getMediaByStorageItemIds(
        storageId: Long,
        storageItemIds: Collection<String>
    ): List<Media>

    @Query("SELECT uri FROM media WHERE id = :mediaId")
    fun getUri(mediaId: Long): Uri

    @Query("SELECT uri FROM media WHERE id IN (:mediaIds)")
    fun getUris(mediaIds: Collection<Long>): List<Uri>

    @Query("SELECT * FROM media WHERE uri=:uri")
    fun getImageByPath(uri: Uri): Flow<Media?>

    @Upsert
    suspend fun upsert(media: Media): Long

    @Upsert
    suspend fun upsertAll(media: Collection<Media>): List<Long>

    @Query("DELETE FROM media WHERE id IN (:mediaIds)")
    suspend fun deleteAll(mediaIds: Collection<Long>)

    @Query("DELETE FROM media WHERE storageId = :storageId")
    suspend fun deleteStorage(storageId: Long)

    @Query("DELETE FROM media WHERE storageId=:storageId AND id NOT IN (:mediaIds)")
    suspend fun deleteAllExcept(storageId: Long, mediaIds: Collection<Long>)
}
