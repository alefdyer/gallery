package com.asinosoft.gallery.data.storage.yandex

import com.google.gson.annotations.SerializedName

data class Download(
    @SerializedName("method")
    val method: String,
    @SerializedName("href")
    val href: String,
    @SerializedName("templated")
    val templated: Boolean
)
