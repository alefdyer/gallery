package com.asinosoft.gallery.data

import android.net.Uri

interface MediaRepository {
    fun fetchAll(): List<Media>

    fun fetchOne(uri: Uri): Media
}
