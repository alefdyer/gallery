package com.asinosoft.gallery.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface AlbumDao {
    @Query("SELECT * FROM album")
    fun getAlbums(): Flow<List<Album>>

    @Upsert
    suspend fun upsert(album: Album)

    @Upsert
    suspend fun upsertAll(images: List<Album>)
}