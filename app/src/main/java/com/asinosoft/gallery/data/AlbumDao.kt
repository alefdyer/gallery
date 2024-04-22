package com.asinosoft.gallery.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

@Dao
interface AlbumDao {
    @Query("SELECT * FROM album")
    suspend fun getAlbums(): List<Album>

    @Query("SELECT * FROM album WHERE name=:name")
    fun getAlbumByName(name: String): Album?

    @Upsert
    suspend fun upsert(album: Album)

    @Upsert
    suspend fun upsertAll(images: List<Album>)
}