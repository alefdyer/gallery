package com.asinosoft.gallery.data

import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AlbumService @Inject constructor(
    private val albumDao: AlbumDao,
    private val analytics: AppAnalytics,
) {
    suspend fun moveAlbumIntoCategory(album: Album, category: AlbumCategory) {
        // Берём актуальный альбом из БД: в drag-payload может быть устаревшее имя
        val current = runCatching { albumDao.getAlbumById(album.id) }.getOrNull() ?: return
        if (current.categoryId == category.id) return

        val sameNameAlbum = albumDao.getAlbumByName(current.name, category.id)
        if (null == sameNameAlbum) {
            albumDao.moveAlbumIntoCategory(current, category)
        } else if (sameNameAlbum.id == current.id) {
            return
        } else {
            val mediaIds = albumDao.getMediaInAlbum(current.id).first().map { it.id }
            albumDao.removeMediaFromAlbum(mediaIds, current.id)
            albumDao.addMediaToAlbum(mediaIds, sameNameAlbum.id)
            albumDao.delete(current.id)
            albumDao.updateAlbumStats(sameNameAlbum.id)
        }
        analytics.albumMoved()
    }

    suspend fun moveAlbumIntoNewCategory(album: Album, categoryName: String) {
        val current = runCatching { albumDao.getAlbumById(album.id) }.getOrNull() ?: return
        val createdCategory = albumDao.findCategoryByName(categoryName) == null
        albumDao.moveAlbumIntoNewCategory(current, categoryName)
        if (createdCategory) analytics.categoryCreated()
        analytics.albumMoved()
    }
}
