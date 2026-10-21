package com.asinosoft.gallery.data

import kotlinx.coroutines.flow.first
import java.io.File
import javax.inject.Inject

class AlbumService @Inject constructor(
    private val albumDao: AlbumDao,
    private val coverService: AlbumCoverService,
    private val analytics: AppAnalytics,
) {
    suspend fun deleteEmptyAlbums() {
        albumDao.getEmptyAlbumIds().forEach { albumId ->
            albumDao.delete(albumId)
            coverService.deleteAlbumCover(albumId)
        }
    }

    suspend fun deleteAlbum(album: Album) {
        albumDao.clearAlbumMedia(album.id)
        coverService.deleteAlbumCover(album.id)
    }

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
            coverService.deleteAlbumCover(current.id)
            updateAlbumCover(sameNameAlbum.id)
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

    suspend fun updateAlbumCover(albumId: Long): File? {
        val file = coverService.updateAlbumCover(albumId, getCoverSources(albumId))
        if (null != file) coverService.deleteOtherCovers(albumId, file)
        return file
    }

    private suspend fun getCoverSources(albumId: Long): List<Media> {
        val photos = albumDao.getMediaForCover(albumId)
        return when {
            photos.size >= 4 -> photos.take(4)
            photos.size >= 2 -> photos.take(2)
            photos.size == 1 -> photos
            else -> listOf()
        }
    }
}
