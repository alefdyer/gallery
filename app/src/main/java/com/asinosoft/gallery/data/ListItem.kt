package com.asinosoft.gallery.data

sealed interface ListItem

data class MediaItem(val media: Media) : ListItem

data class HeaderItem(val label: String, val mediaIds: Set<Long>) : ListItem
