package com.asinosoft.photogallery.data.storage.yandex

import com.google.gson.annotations.SerializedName

data class Thumbnail(
    @SerializedName("url")
    val url: String,
    @SerializedName("name")
    val name: String
)
