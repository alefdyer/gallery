package com.asinosoft.gallery.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import java.util.UUID

@Entity(
    tableName = "media_album",
    primaryKeys = ["mediaId", "albumId"],
    foreignKeys = [
        ForeignKey(
            entity = Media::class,
            parentColumns = ["id"],
            childColumns = ["mediaId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Album::class,
            parentColumns = ["id"],
            childColumns = ["albumId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["mediaId"]),
        Index(value = ["albumId"])
    ]
)
data class MediaAlbum(val mediaId: UUID, val albumId: UUID)
