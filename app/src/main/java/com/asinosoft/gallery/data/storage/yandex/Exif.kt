package com.asinosoft.gallery.data.storage.yandex

import com.google.gson.annotations.SerializedName
import java.util.Date

data class Exif(
    @SerializedName("date_time")
    val datetime: Date?
)
