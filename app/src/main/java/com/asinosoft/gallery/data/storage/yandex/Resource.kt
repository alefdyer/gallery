package com.asinosoft.gallery.data.storage.yandex

import com.google.gson.annotations.SerializedName
import java.util.Date

data class Resource(
    @SerializedName("file")
    val file: String,
    @SerializedName("name")
    val name: String,
    @SerializedName("path")
    val path: String,
    @SerializedName("type")
    val type: String,
    @SerializedName("media_type")
    val mediaType: String,
    @SerializedName("mime_type")
    val mimeType: String,
    @SerializedName("size")
    val size: Long,
    @SerializedName("created")
    val created: Date,
    @SerializedName("modifier")
    val modified: Date,
    @SerializedName("exif")
    val exif: Exif?
)
