package com.asinosoft.gallery.data

interface ImageRepository {
    fun fetchAll(): List<Image>

    fun fetchOne(path: String): Image
}