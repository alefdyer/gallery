package com.asinosoft.photogallery.data.storage

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert

@Dao
interface FolderSnapshotDao {
    @Query("SELECT * FROM folder_snapshot WHERE storageId = :storageId")
    suspend fun getForStorage(storageId: Long): List<FolderSnapshot>

    @Upsert
    suspend fun upsertAll(rows: List<FolderSnapshot>)

    @Query("DELETE FROM folder_snapshot WHERE storageId = :storageId")
    suspend fun deleteForStorage(storageId: Long)

    @Transaction
    suspend fun replaceForStorage(storageId: Long, rows: List<FolderSnapshot>) {
        deleteForStorage(storageId)
        if (rows.isEmpty()) return
        upsertAll(rows.map { it.copy(storageId = storageId) })
    }
}
