package com.asinosoft.photogallery.data.storage

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Фасад над Room: `password` в [StorageCredentialStore], в БД колонка пустая.
 */
@Singleton
class StorageDao @Inject constructor(
    private val room: StorageRoomDao,
    private val credentials: StorageCredentialStore,
) {
    suspend fun getStorageById(id: Long): Storage = hydrate(room.getStorageById(id))

    fun getAccounts(): Flow<List<Storage>> =
        room.getAccounts().map { list -> list.map { hydrate(it) } }

    fun getStorages(): Flow<List<Storage>> =
        room.getStorages().map { list -> list.map { hydrate(it) } }

    suspend fun upsert(storage: Storage): Long {
        val secret = storage.password
        val rowId = room.upsert(storage.copy(password = null))
        val id = when {
            storage.id != 0L -> storage.id
            rowId > 0L -> rowId
            else -> storage.id
        }
        if (secret != null) {
            credentials.put(id, secret)
        }
        return if (storage.id == 0L && rowId > 0L) rowId else storage.id
    }

    suspend fun delete(storage: Storage) {
        room.delete(storage)
        credentials.remove(storage.id)
    }

    /** Перенос plaintext из Room в encrypted prefs (upgrade). */
    suspend fun migratePlaintextPasswords() {
        for (row in room.getStoragesOnce()) {
            val plain = row.password?.takeIf { it.isNotEmpty() } ?: continue
            if (credentials.get(row.id) == null) {
                credentials.put(row.id, plain)
            }
            room.upsert(row.copy(password = null))
        }
    }

    private fun hydrate(storage: Storage): Storage {
        val fromStore = credentials.get(storage.id)
        if (fromStore != null) {
            return if (storage.password == fromStore) storage
            else storage.copy(password = fromStore)
        }
        val legacy = storage.password?.takeIf { it.isNotEmpty() } ?: return storage
        // Ленивая миграция одной строки
        credentials.put(storage.id, legacy)
        return storage
    }
}
