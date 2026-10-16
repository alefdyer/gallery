package com.asinosoft.gallery.data.storage

data class MediaStatistics(
    val photoCount: Int = 0,
    val videoCount: Int = 0
): Comparable<MediaStatistics> {
    val total: Int
        get() = photoCount + videoCount

    override operator fun compareTo(other: MediaStatistics): Int =
        if (total == other.total) 0
        else if (photoCount == other.photoCount) videoCount - other.videoCount
        else photoCount - other.videoCount
}
