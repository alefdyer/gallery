package com.asinosoft.gallery.data

interface MediaRepository {
    fun fetchAll(): List<Media>

    fun fetchOne(path: String): Media
}
