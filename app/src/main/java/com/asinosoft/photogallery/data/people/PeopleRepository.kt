package com.asinosoft.photogallery.data.people

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Singleton
class PeopleRepository @Inject constructor(
    private val personDao: PersonDao,
    private val peopleAlbums: PeopleAlbums,
) {
    fun observePeople(): Flow<List<Person>> = personDao.observePeople()

    fun observePersonByAlbumId(albumId: Long): Flow<Person?> =
        personDao.observePersonByAlbumId(albumId)

    fun observePeopleCoverThumbs(): Flow<Map<Long, String>> =
        personDao.observeCoverThumbs().map { rows ->
            rows.mapNotNull { row ->
                row.thumbPath?.takeIf { it.isNotBlank() }?.let { row.albumId to it }
            }.toMap()
        }

    suspend fun getPerson(id: Long): Person? = personDao.getPerson(id)

    suspend fun getPersonByAlbumId(albumId: Long): Person? =
        personDao.getPersonByAlbumId(albumId)

    suspend fun getNamedPeople(): List<Person> = personDao.getNamedPeople()

    suspend fun getFacesForPerson(personId: Long): List<Face> =
        personDao.getFacesForPerson(personId).filter { it.right - it.left > 0.01f }

    suspend fun updatePerson(
        personId: Long,
        displayName: String?,
        relationship: String?,
        contactUri: String?,
        contactId: Long?,
    ) {
        val person = personDao.getPerson(personId) ?: return
        val name = displayName?.trim()?.takeIf { it.isNotEmpty() }
        personDao.updatePerson(
            person.copy(
                displayName = name,
                relationship = relationship?.trim()?.takeIf { it.isNotEmpty() },
                contactUri = contactUri,
                contactId = contactId,
                updatedAt = System.currentTimeMillis(),
            )
        )
        peopleAlbums.syncPerson(personId)
    }

    suspend fun setCoverFace(personId: Long, faceId: Long) {
        peopleAlbums.setCoverFace(personId, faceId)
    }

    /**
     * Объединяет source → target. Имя/родство/контакт: target wins если заполнены.
     */
    suspend fun mergePeople(sourcePersonId: Long, targetPersonId: Long) {
        if (sourcePersonId == targetPersonId) return
        val source = personDao.getPerson(sourcePersonId) ?: return
        val target = personDao.getPerson(targetPersonId) ?: return

        personDao.moveFaces(sourcePersonId, targetPersonId)

        val merged = target.copy(
            displayName = target.displayName ?: source.displayName,
            relationship = target.relationship ?: source.relationship,
            contactUri = target.contactUri ?: source.contactUri,
            contactId = target.contactId ?: source.contactId,
            coverFaceId = target.coverFaceId ?: source.coverFaceId,
            updatedAt = System.currentTimeMillis(),
        )
        personDao.updatePerson(merged)
        peopleAlbums.deletePersonAlbum(source)
        peopleAlbums.syncPerson(targetPersonId)
    }

    /**
     * Выделенные лица → новый Person; остальные остаются у исходного.
     */
    suspend fun splitFaces(sourcePersonId: Long, faceIds: Collection<Long>): Long? {
        if (faceIds.isEmpty()) return null
        val source = personDao.getPerson(sourcePersonId) ?: return null
        val allFaces = personDao.getFacesForPerson(sourcePersonId)
            .filter { it.right - it.left > 0.01f }
        val move = allFaces.filter { it.id in faceIds }
        if (move.isEmpty() || move.size >= allFaces.size) return null

        val newId = personDao.insertPerson(Person())
        personDao.assignFacesToPerson(move.map { it.id }, newId)

        if (source.coverFaceId != null && source.coverFaceId in faceIds) {
            personDao.updatePerson(
                source.copy(coverFaceId = null, updatedAt = System.currentTimeMillis())
            )
        }

        peopleAlbums.syncPerson(sourcePersonId)
        peopleAlbums.syncPerson(newId)
        return newId
    }
}
