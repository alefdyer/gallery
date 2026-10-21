package com.asinosoft.gallery.data

import android.net.Uri
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import com.asinosoft.gallery.data.storage.MediaStatistics
import com.asinosoft.gallery.data.storage.StorageType
import kotlinx.coroutines.flow.Flow

data class MediaUri(val id: Long, val uri: Uri)

/** Лёгкая строка для сопоставления медиа при восстановлении бэкапа. */
data class MediaBackupLookup(
    val id: Long,
    val path: String,
    val filename: String,
    val storageType: StorageType,
    val owner: String?,
    val size: Long,
)

@Dao
interface MediaDao {
    @Query("SELECT * FROM media WHERE storageId=:storageId ORDER BY date DESC, time DESC")
    fun getImages(storageId: Long): Flow<List<Media>>

    @Query("SELECT * FROM media WHERE storageId=:storageId AND storageItemId IN (:storageItemIds)")
    suspend fun getMediaByStorageItemIds(
        storageId: Long,
        storageItemIds: Collection<String>
    ): List<Media>

    @Query("SELECT * FROM media WHERE favorite = 1 ORDER BY date DESC, time DESC")
    fun getFavorites(): Flow<List<Media>>

    @Query("SELECT * FROM media WHERE favorite = 1")
    suspend fun getFavoritesList(): List<Media>

    @Query(
        """
        SELECT * FROM media
        WHERE path = :path
          AND (filename = :name OR filename LIKE '%/' || :name)
        LIMIT 20
        """
    )
    suspend fun findByPathAndName(path: String, name: String): List<Media>

    @Query("UPDATE media SET favorite = :favorite WHERE id IN (:mediaIds)")
    suspend fun setFavorite(mediaIds: Collection<Long>, favorite: Boolean)

    /** Сброс всего избранного (для восстановления в режиме перезаписи). */
    @Query("UPDATE media SET favorite = 0")
    suspend fun clearAllFavorites()

    @Query("SELECT * FROM media WHERE storageId = :storageId AND id NOT IN (SELECT mediaId FROM media_album)")
    suspend fun getMediaWithoutAlbum(storageId: Long): List<Media>

    @Query("SELECT id FROM media WHERE storageId = :storageId AND storageItemId = :storageItemId")
    suspend fun getMediaId(storageId: Long, storageItemId: String): Long?

    @Query("SELECT * FROM media WHERE storageId = :storageId ORDER BY mimeType='image/jpeg' DESC, date, time LIMIT 1")
    suspend fun getStorageCover(storageId: Long): Media?

    @Query("SELECT COUNT(*) FROM media")
    fun observeMediaCount(): Flow<Int>

    @Query("SELECT id, path, filename, storageType, owner, size FROM media")
    suspend fun getAllBackupLookupRows(): List<MediaBackupLookup>

    @Query("SELECT COUNT(video) AS videoCount, COUNT(CASE WHEN video IS NULL THEN 1 ELSE NULL END) AS photoCount FROM media WHERE storageId = :storageId")
    suspend fun getStorageStatistics(storageId: Long): MediaStatistics

    @Query("SELECT id, uri FROM media WHERE video IS NOT NULL")
    suspend fun getVideoUris(): List<MediaUri>

    @Query("SELECT uri FROM media WHERE id = :mediaId")
    fun getUri(mediaId: Long): Uri

    @Query("SELECT uri FROM media WHERE id IN (:mediaIds)")
    fun getUris(mediaIds: Collection<Long>): List<Uri>

    @Query("SELECT * FROM media WHERE uri=:uri")
    fun getImageByPath(uri: Uri): Flow<Media?>

    @Query("SELECT * FROM media WHERE id IN (:mediaIds)")
    suspend fun getByIds(mediaIds: Collection<Long>): List<Media>

    @Query("SELECT * FROM media WHERE filename = :filename LIMIT 1")
    suspend fun findByFilename(filename: String): Media?

    @Query("SELECT * FROM media WHERE size = :size AND (filename = :name OR filename LIKE '%/' || :name) LIMIT 1")
    suspend fun findByNameAndSize(name: String, size: Long): Media?

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
