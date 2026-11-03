package com.asinosoft.photogallery.data.people

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.asinosoft.photogallery.GalleryApp
import com.asinosoft.photogallery.data.Album
import com.asinosoft.photogallery.data.AlbumCategory
import com.asinosoft.photogallery.data.AlbumDao
import com.asinosoft.photogallery.data.MediaAlbum
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Системная категория «Люди»: по одному альбому на [Person].
 * Имя альбома стабильно (`:person:{id}`); отображаемое имя берётся из Person.
 */
@Singleton
class PeopleAlbums @Inject constructor(
    private val albumDao: AlbumDao,
    private val personDao: PersonDao,
    @param:ApplicationContext private val context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    suspend fun categoryId(): Long = mutex.withLock {
        val savedId = prefs.getLong(KEY_CATEGORY_ID, 0L)
        if (savedId > 0) {
            albumDao.getCategoryById(savedId)?.let { return@withLock savedId }
        }
        val existing = albumDao.findCategoryByName(AlbumCategory.PEOPLE_NAME)
        val id = existing?.id
            ?: albumDao.createCategory(AlbumCategory(name = AlbumCategory.PEOPLE_NAME))
        prefs.edit { putLong(KEY_CATEGORY_ID, id) }
        id
    }

    /** Создаёт категорию заранее, чтобы секция была видна до появления лиц. */
    suspend fun ensureCategory() {
        categoryId()
    }

    suspend fun syncPerson(personId: Long) {
        val person = personDao.getPerson(personId) ?: return
        val catId = categoryId()
        val albumName = albumNameFor(personId)
        var albumId = person.albumId
        var album = albumId?.let { runCatching { albumDao.getAlbumById(it) }.getOrNull() }

        if (album == null) {
            album = albumDao.getAlbumByName(albumName, catId)
                ?: Album(name = albumName, categoryId = catId, userCreated = false)
                    .let { it.copy(id = albumDao.upsert(it)) }
            albumId = album.id
            personDao.updatePerson(
                person.copy(
                    albumId = albumId,
                    updatedAt = System.currentTimeMillis()
                )
            )
        } else if (album.categoryId != catId || album.name != albumName) {
            albumDao.upsert(album.copy(categoryId = catId, name = albumName))
        }

        val mediaIds = personDao.getMediaIdsForPerson(personId).distinct()
        albumDao.clearAlbumMedia(albumId!!)
        if (mediaIds.isNotEmpty()) {
            albumDao.insertMedia(mediaIds.map { MediaAlbum(mediaId = it, albumId = albumId) })
        }

        var coverFaceId = person.coverFaceId
        val realFaces = personDao.getFacesForPerson(personId)
            .filter { PersonClusterer.isClusterableFace(it) }
        if (coverFaceId == null || realFaces.none { it.id == coverFaceId }) {
            coverFaceId = realFaces.firstOrNull { !it.thumbPath.isNullOrBlank() }?.id
                ?: realFaces.firstOrNull()?.id
            if (coverFaceId != person.coverFaceId) {
                personDao.updatePerson(
                    person.copy(coverFaceId = coverFaceId, updatedAt = System.currentTimeMillis())
                )
            }
        }

        val coverMediaId = resolveCoverMediaId(
            person.copy(coverFaceId = coverFaceId),
            mediaIds,
        )
        val stats = albumDao.getAlbumStats(albumId)
        albumDao.setAlbumStats(
            albumId = albumId,
            count = mediaIds.size,
            photoCount = mediaIds.size,
            videoCount = 0,
            size = stats.size,
            coverId = coverMediaId ?: 0L,
            date = if (stats.count > 0) stats.date else LocalDate.now(),
        )
    }

    suspend fun syncAll() {
        personDao.getAllPeople().forEach { person ->
            val faces = personDao.getFacesForPerson(person.id)
                .filter { PersonClusterer.isClusterableFace(it) }
            if (faces.isEmpty()) {
                deletePersonAlbum(person)
            } else {
                syncPerson(person.id)
            }
        }
    }

    suspend fun deletePersonAlbum(person: Person) {
        person.albumId?.let { albumId ->
            runCatching {
                albumDao.clearAlbumMedia(albumId)
                albumDao.delete(albumId)
            }.onFailure {
                Log.w(GalleryApp.TAG, "Can't delete people album $albumId", it)
            }
        }
        personDao.deletePerson(person.id)
    }

    suspend fun setCoverFace(personId: Long, faceId: Long) {
        val person = personDao.getPerson(personId) ?: return
        val face = personDao.getFace(faceId) ?: return
        if (face.personId != personId) return
        personDao.updatePerson(
            person.copy(coverFaceId = faceId, updatedAt = System.currentTimeMillis())
        )
        syncPerson(personId)
    }

    private suspend fun resolveCoverMediaId(person: Person, mediaIds: List<Long>): Long? {
        person.coverFaceId?.let { faceId ->
            personDao.getFace(faceId)?.mediaId?.takeIf { it in mediaIds }?.let { return it }
        }
        return mediaIds.firstOrNull()
    }

    companion object {
        const val PREFS_NAME = "people_albums"
        const val KEY_CATEGORY_ID = "category_id"

        fun albumNameFor(personId: Long): String = ":person:$personId"
    }
}
