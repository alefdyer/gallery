package com.asinosoft.gallery.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM album WHERE id = :id")
    suspend fun getAlbumById(id: Long): Album?

    @Query("SELECT * FROM album ORDER BY name")
    fun getAlbums(): Flow<List<Album>>

    @Upsert
    suspend fun upsert(album: Album): Long

    @Query("DELETE FROM album WHERE id = :albumId")
    suspend fun delete(albumId: Long)

    @Delete
    suspend fun deleteAll(albums: List<Album>)

    @Query(
        """
        DELETE FROM album WHERE id IN (
            SELECT a.id
            FROM album a
            LEFT JOIN media_album ma ON ma.albumId = a.id
            WHERE ma.mediaId IS NULL
        )
        """
    )
    suspend fun deleteEmptyAlbums()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMedia(links: List<MediaAlbum>)

    @Transaction
    suspend fun addMediaToAlbum(mediaIds: Collection<Long>, albumId: Long) {
        if (mediaIds.isEmpty()) {
            return
        }
        insertMedia(mediaIds.map { MediaAlbum(mediaId = it, albumId = albumId) })
    }

    @Query(
        "DELETE FROM media_album WHERE albumId = :albumId AND mediaId IN (:mediaIds)"
    )
    suspend fun removeMediaFromAlbum(mediaIds: Collection<Long>, albumId: Long)

    @Query(
        """
        SELECT m.*
        FROM media m
        INNER JOIN media_album ma ON ma.mediaId = m.id
        WHERE ma.albumId = :albumId
        ORDER BY m.date DESC, m.time DESC
        """
    )
    fun getMediaInAlbum(albumId: Long): Flow<List<Media>>

    @Query(
        "SELECT DISTINCT albumId FROM media_album WHERE mediaId IN (:mediaIds)"
    )
    suspend fun getMediaAlbumIds(mediaIds: Collection<Long>): List<Long>

    @Query(
        """
        SELECT
            a.name AS name,
            COUNT(m.id) AS count,
            IFNULL(SUM(m.size), 0) AS size,
            COALESCE(
                (
                    SELECT m2.uri
                    FROM media_album ma2
                    INNER JOIN media m2 ON m2.id = ma2.mediaId
                    WHERE ma2.albumId = a.id AND m2.uri = a.cover
                    LIMIT 1
                ),
                (
                    SELECT m2.uri
                    FROM media_album ma2
                    INNER JOIN media m2 ON m2.id = ma2.mediaId
                    WHERE ma2.albumId = a.id
                    ORDER BY m2.date DESC, m2.time DESC
                    LIMIT 1
                )
            ) AS cover,
            IFNULL(MAX(m.date), a.date) AS date
        FROM album a
        LEFT JOIN media_album ma ON ma.albumId = a.id
        LEFT JOIN media m ON m.id = ma.mediaId
        WHERE a.id = :albumId
        GROUP BY a.id, a.name, a.date
        ORDER BY a.name
        """
    )
    suspend fun getAlbumStats(albumId: Long): AlbumStats
}
