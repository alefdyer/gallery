package com.asinosoft.gallery.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM album ORDER BY name")
    fun getAlbums(): Flow<List<Album>>

    @Query("SELECT * FROM album WHERE name=:name")
    fun getAlbumByName(name: String): Album?

    @Query("SELECT a.* FROM album a LEFT JOIN media m on m.album=a.name WHERE m.id IS NULL")
    suspend fun getEmptyAlbums(): List<Album>

    @Upsert
    suspend fun upsert(album: Album)

    @Upsert
    suspend fun upsertAll(albums: List<Album>)

    @Delete
    suspend fun deleteAll(albums: List<Album>)
}
