package com.asinosoft.gallery.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {
    @Query("SELECT * FROM image ORDER BY date DESC")
    fun getImages(): Flow<List<Image>>

    @Query("""
        SELECT
            album as name,
            count(*) as count,
            sum(size) as size,
            max(path) as cover
        FROM image
        GROUP BY album
    """)
    fun getAlbums(): List<Album>

    @Upsert
    suspend fun upsert(image: Image)

    @Upsert
    suspend fun upsertAll(images: List<Image>)

    @Delete
    suspend fun deleteAll(images: List<Image>)
}
