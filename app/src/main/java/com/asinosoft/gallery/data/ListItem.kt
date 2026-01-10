package com.asinosoft.gallery.data

sealed interface ListItem

data class ImageItem(val image: Image): ListItem

data class HeaderItem(val label: String): ListItem
