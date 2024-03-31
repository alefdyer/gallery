package com.asinosoft.gallery.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ImageDao {
    @Query("SELECT * FROM images ORDER BY date DESC")
    fun getImages(): Flow<List<Image>>

    @Upsert
    suspend fun upsert(image: Image)

    @Upsert
    suspend fun upsertAll(images: List<Image>)

    @Delete
    suspend fun deleteAll(images: List<Image>)
}
