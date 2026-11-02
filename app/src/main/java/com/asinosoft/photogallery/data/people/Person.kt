package com.asinosoft.photogallery.data.people

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "person",
    indices = [
        Index(value = ["albumId"], unique = true),
    ],
)
data class Person(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val displayName: String? = null,
    /** Ключ родства (me, mother, …) или произвольный текст для «Другое». */
    val relationship: String? = null,
    val contactUri: String? = null,
    val contactId: Long? = null,
    val coverFaceId: Long? = null,
    /** Связанный альбом в категории :people. */
    val albumId: Long? = null,
    val updatedAt: Long = System.currentTimeMillis(),
)
