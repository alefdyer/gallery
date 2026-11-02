package com.asinosoft.photogallery.data.people

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.asinosoft.photogallery.data.Media

@Entity(
    tableName = "face",
    indices = [
        Index(value = ["mediaId"]),
        Index(value = ["personId"]),
    ],
    foreignKeys = [
        ForeignKey(
            entity = Media::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = Person::class,
            parentColumns = ["id"],
            childColumns = ["personId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
)
data class Face(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val mediaId: Long,
    val personId: Long? = null,
    /** Нормализованный bbox 0..1 относительно исходного кадра. */
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
    /** Компактный эмбеддинг для кластеризации (float LE bytes). */
    val embedding: ByteArray,
    val thumbPath: String? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Face) return false
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
}
