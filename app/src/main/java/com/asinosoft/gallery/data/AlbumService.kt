package com.asinosoft.gallery.data

import kotlinx.coroutines.flow.first
import javax.inject.Inject

class AlbumService @Inject constructor(
    private val albumDao: AlbumDao,
) {
    suspend fun moveAlbumIntoCategory(album: Album, category: AlbumCategory) {
        val sameNameAlbum = albumDao.getAlbumByName(album.name, category.id)
        if (null == sameNameAlbum) {
            albumDao.moveAlbumIntoCategory(album, category)
        } else {
            val mediaIds = albumDao.getMediaInAlbum(album.id).first().map { it.id }
            albumDao.removeMediaFromAlbum(mediaIds, album.id)
            albumDao.addMediaToAlbum(mediaIds, sameNameAlbum.id)
            albumDao.delete(album.id)
            albumDao.updateAlbumStats(sameNameAlbum.id)
        }
    }

    suspend fun moveAlbumIntoNewCategory(album: Album, categoryName: String) {
        albumDao.moveAlbumIntoNewCategory(album, categoryName)
    }
}