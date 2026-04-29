package com.asinosoft.gallery.data.storage.yandex

import com.google.gson.annotations.SerializedName

data class ResourceList(
    @SerializedName("items")
    val items: List<Resource>,
    @SerializedName("limit")
    val limit: Int,
    @SerializedName("offset")
    val offset: Int,
    @SerializedName("total")
    val total: Int,
    @SerializedName("path")
    val path: String,
    @SerializedName("sort")
    val sort: String
)
