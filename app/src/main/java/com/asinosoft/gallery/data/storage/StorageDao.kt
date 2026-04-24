package com.asinosoft.gallery.data.storage

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface StorageDao {
    @Query("SELECT * FROM storage WHERE id = :id")
    suspend fun getStorageById(id: Long): Storage

    @Query("SELECT * FROM storage ORDER BY type, name")
    fun getAccounts(): Flow<List<Storage>>

    @Query("SELECT * FROM storage")
    suspend fun getStorages(): List<Storage>

    @Upsert
    suspend fun upsert(storage: Storage): Long

    @Delete
    suspend fun delete(storage: Storage)
}
