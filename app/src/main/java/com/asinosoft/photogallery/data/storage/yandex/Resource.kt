package com.asinosoft.photogallery.data.storage.yandex

import com.google.gson.annotations.SerializedName
import java.util.Date

data class Resource(
    @SerializedName("file")
    val file: String? = null,
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
    @SerializedName("modified")
    val modified: Date? = null,
    @SerializedName("sizes")
    val sizes: List<Thumbnail>? = null,
    @SerializedName("exif")
    val exif: Exif? = null
)
