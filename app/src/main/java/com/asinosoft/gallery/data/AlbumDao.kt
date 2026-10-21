package com.asinosoft.gallery.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import com.asinosoft.gallery.data.storage.StorageType
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

data class AlbumMediaSource(
    val albumId: Long,
    val path: String,
    val owner: String?
)

data class AlbumMediaRef(
    val path: String,
    val filename: String,
    val storageType: StorageType,
    val owner: String?,
    val size: Long,
)

@Dao
interface AlbumDao {
    @Query("SELECT * FROM album WHERE id = :id")
    suspend fun getAlbumById(id: Long): Album

    @Query("SELECT * FROM album WHERE name = :name AND categoryId = :categoryId")
    suspend fun getAlbumByName(name: String, categoryId: Long): Album?

    @Query("SELECT * FROM album WHERE name = :name ORDER BY id LIMIT 1")
    suspend fun findAlbumByName(name: String): Album?

    @Query("SELECT * FROM album WHERE name = :name ORDER BY id")
    suspend fun findAlbumsByName(name: String): List<Album>

    @Query("SELECT * FROM album WHERE name IN (SELECT name FROM album GROUP BY name HAVING COUNT(*) > 1) ORDER BY id")
    suspend fun getDuplicateAlbums(): List<Album>

    @Query("INSERT OR IGNORE INTO media_album (mediaId, albumId) SELECT mediaId, :toAlbumId FROM media_album WHERE albumId = :fromAlbumId")
    suspend fun copyAlbumMedia(fromAlbumId: Long, toAlbumId: Long)

    @Transaction
    suspend fun mergeAlbums(fromAlbumId: Long, toAlbumId: Long) {
        copyAlbumMedia(fromAlbumId, toAlbumId)
        delete(fromAlbumId)
        updateAlbumStats(toAlbumId)
    }

    @Query(
        """
        SELECT DISTINCT ma.albumId AS albumId, m.path AS path, m.owner AS owner
        FROM media_album ma
        INNER JOIN media m ON m.id = ma.mediaId
        INNER JOIN album a ON a.id = ma.albumId
        WHERE a.categoryId = :categoryId
        """
    )
    suspend fun getAlbumMediaSources(categoryId: Long): List<AlbumMediaSource>

    suspend fun getOrCreateAlbum(name: String, categoryId: Long): Album =
        getAlbumByName(name, categoryId)
            ?: Album(name = name, categoryId = categoryId).let { it.copy(id = upsert(it)) }

    @Transaction
    @Query("SELECT * FROM album ORDER BY name")
    fun getAlbums(): Flow<List<AlbumWithCover>>

    /** Альбомы той же категории, что и альбом [albumId] — лёгкий список соседей для листания свайпом. */
    @Transaction
    @Query("SELECT * FROM album WHERE categoryId = (SELECT categoryId FROM album WHERE id = :albumId)")
    fun getCategoryAlbums(albumId: Long): Flow<List<AlbumWithCover>>

    @Transaction
    @Query("SELECT * FROM album WHERE id IN (SELECT albumId FROM media_album WHERE mediaId = :mediaId) ORDER BY name")
    fun getMediaAlbums(mediaId: Long): Flow<List<AlbumWithCover>>

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
            COUNT(m.id) AS count,
            IFNULL(SUM(m.size), 0) AS size,
            (
                SELECT m2.id
                FROM media_album ma2
                INNER JOIN media m2 ON m2.id = ma2.mediaId
                WHERE ma2.albumId = a.id
                ORDER BY m2.date DESC, m2.time DESC
                LIMIT 1
            ) AS coverId,
            IFNULL(MAX(m.date), a.date) AS date
        FROM album a
        LEFT JOIN media_album ma ON ma.albumId = a.id
        LEFT JOIN media m ON m.id = ma.mediaId
        WHERE a.id = :albumId
        """
    )
    suspend fun getAlbumStats(albumId: Long): AlbumStats

    @Query("UPDATE album SET count = :count, size = :size, coverId = :coverId, date = :date WHERE id = :albumId")
    suspend fun setAlbumStats(albumId: Long, count: Int, size: Long, coverId: Long, date: LocalDate)

    suspend fun updateAlbumStats(albumId: Long) {
        val stats = getAlbumStats(albumId)
        if (stats.count > 0) {
            setAlbumStats(albumId, stats.count, stats.size, stats.coverId, stats.date)
        } else {
            delete(albumId)
        }
    }

    @Query("SELECT * FROM album_category ORDER BY name")
    fun getAlbumCategories(): Flow<List<AlbumCategory>>

    @Query("SELECT * FROM album_category ORDER BY name")
    suspend fun getAllCategories(): List<AlbumCategory>

    @Query("SELECT * FROM album")
    suspend fun getAllAlbums(): List<Album>

    @Query(
        """
        SELECT m.path AS path, m.filename AS filename, m.storageType AS storageType,
               m.owner AS owner, m.size AS size
        FROM media m
        INNER JOIN media_album ma ON ma.mediaId = m.id
        WHERE ma.albumId = :albumId
        """
    )
    suspend fun getAlbumMediaRefs(albumId: Long): List<AlbumMediaRef>

    @Query("SELECT * FROM album_category WHERE name = :name")
    suspend fun findCategoryByName(name: String): AlbumCategory?

    @Query("SELECT * FROM album_category WHERE id = :id")
    suspend fun getCategoryById(id: Long): AlbumCategory?

    @Insert
    suspend fun createCategory(albumCategory: AlbumCategory): Long

    @Query("UPDATE album_category SET name = :name WHERE id = :id")
    suspend fun renameCategory(id: Long, name: String)

    @Query("UPDATE album SET name = :name WHERE id = :id")
    suspend fun renameAlbum(id: Long, name: String)

    @Query("UPDATE album SET categoryId = :categoryId WHERE id = :albumId")
    suspend fun setAlbumCategory(albumId: Long, categoryId: Long)

    suspend fun moveAlbumIntoCategory(album: Album, category: AlbumCategory) {
        setAlbumCategory(album.id, category.id)
        deleteEmptyCategories()
    }

    suspend fun moveAlbumIntoNewCategory(album: Album, categoryName: String) {
        val categoryId =
            findCategoryByName(categoryName)?.id
                ?: createCategory(AlbumCategory(name = categoryName))

        setAlbumCategory(album.id, categoryId)
        deleteEmptyCategories()
    }

    @Query("DELETE FROM album_category WHERE name NOT LIKE ':%' AND id IN (SELECT c.id FROM album_category c LEFT JOIN album a ON a.categoryId = c.id GROUP BY c.id HAVING count(a.id) = 0)")
    suspend fun deleteEmptyCategories()
}
