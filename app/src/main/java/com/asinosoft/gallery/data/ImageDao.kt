package com.asinosoft.gallery.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface ImageDao {
    @Query("SELECT * FROM image ORDER BY date DESC")
    suspend fun getImages(): List<Image>

    @Query("SELECT * FROM image WHERE path=:path")
    fun getImageByPath(path: String): Image?

    @Query("SELECT * FROM image WHERE album=:album ORDER BY date DESC")
    suspend fun getAlbumImages(album: String): List<Image>

    @Query(
        """
        SELECT
            album as name,
            count(*) as count,
            sum(size) as size,
            max(path) as cover
        FROM image
        GROUP BY album
    """,
    )
    suspend fun getAlbums(): List<Album>

    @Query(
        """
        SELECT *
        FROM image
        WHERE album=:album
        ORDER BY date DESC
        LIMIT 1
    """,
    )
    suspend fun getAlbumLastImage(album: String): Image

    @Upsert
    suspend fun upsert(image: Image)

    @Upsert
    suspend fun upsertAll(images: List<Image>)

    @Delete
    suspend fun deleteAll(images: List<Image>)
}
