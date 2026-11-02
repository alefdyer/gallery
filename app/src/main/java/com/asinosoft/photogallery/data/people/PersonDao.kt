package com.asinosoft.photogallery.data.people

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class PersonCoverThumb(
    val albumId: Long,
    val thumbPath: String?,
)

@Dao
interface PersonDao {
    @Query("SELECT * FROM person ORDER BY displayName IS NULL, displayName COLLATE NOCASE, id")
    fun observePeople(): Flow<List<Person>>

    @Query("SELECT * FROM person ORDER BY displayName IS NULL, displayName COLLATE NOCASE, id")
    suspend fun getAllPeople(): List<Person>

    @Query("SELECT * FROM person WHERE id = :id")
    suspend fun getPerson(id: Long): Person?

    @Query("SELECT * FROM person WHERE id = :id")
    fun observePerson(id: Long): Flow<Person?>

    @Query("SELECT * FROM person WHERE albumId = :albumId")
    suspend fun getPersonByAlbumId(albumId: Long): Person?

    @Query("SELECT * FROM person WHERE albumId = :albumId")
    fun observePersonByAlbumId(albumId: Long): Flow<Person?>

    @Query("SELECT * FROM person WHERE displayName IS NOT NULL AND trim(displayName) != '' ORDER BY displayName COLLATE NOCASE")
    suspend fun getNamedPeople(): List<Person>

    @Query(
        """
        SELECT p.albumId AS albumId,
            COALESCE(
                (SELECT f.thumbPath FROM face f WHERE f.id = p.coverFaceId AND f.thumbPath IS NOT NULL),
                (SELECT f.thumbPath FROM face f
                 WHERE f.personId = p.id
                   AND (f.right - f.left) > 0.01
                   AND f.thumbPath IS NOT NULL
                 ORDER BY f.id LIMIT 1)
            ) AS thumbPath
        FROM person p
        WHERE p.albumId IS NOT NULL
        """
    )
    fun observeCoverThumbs(): Flow<List<PersonCoverThumb>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPerson(person: Person): Long

    @Update
    suspend fun updatePerson(person: Person)

    @Query("DELETE FROM person WHERE id = :id")
    suspend fun deletePerson(id: Long)

    @Query("SELECT * FROM face WHERE id = :id")
    suspend fun getFace(id: Long): Face?

    @Query("SELECT * FROM face WHERE mediaId = :mediaId")
    suspend fun getFacesForMedia(mediaId: Long): List<Face>

    @Query("SELECT * FROM face WHERE personId = :personId")
    suspend fun getFacesForPerson(personId: Long): List<Face>

    /** Реальные лица без person; маркеры «нет лиц» (нулевой bbox) исключены. */
    @Query(
        """
        SELECT * FROM face
        WHERE personId IS NULL
          AND (right - left) > 0.01 AND (bottom - top) > 0.01
        """
    )
    suspend fun getUnassignedFaces(): List<Face>

    @Query("SELECT * FROM face")
    suspend fun getAllFaces(): List<Face>

    @Query(
        """
        SELECT DISTINCT mediaId FROM face
        WHERE personId = :personId
          AND (right - left) > 0.01 AND (bottom - top) > 0.01
        """
    )
    suspend fun getMediaIdsForPerson(personId: Long): List<Long>

    @Query(
        """
        SELECT * FROM face
        WHERE (right - left) > 0.01 AND (bottom - top) > 0.01
        """
    )
    suspend fun getRealFaces(): List<Face>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFace(face: Face): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFaces(faces: List<Face>): List<Long>

    @Update
    suspend fun updateFace(face: Face)

    @Query("UPDATE face SET personId = :personId WHERE id IN (:faceIds)")
    suspend fun assignFacesToPerson(faceIds: Collection<Long>, personId: Long)

    @Query("UPDATE face SET personId = :targetPersonId WHERE personId = :sourcePersonId")
    suspend fun moveFaces(sourcePersonId: Long, targetPersonId: Long)

    @Query("DELETE FROM face WHERE mediaId = :mediaId")
    suspend fun deleteFacesForMedia(mediaId: Long)

    @Query("SELECT COUNT(*) FROM face WHERE mediaId = :mediaId")
    suspend fun faceCountForMedia(mediaId: Long): Int

    @Query(
        """
        SELECT m.id FROM media m
        WHERE m.storageType = 'LOCAL' AND m.video IS NULL
          AND m.id NOT IN (SELECT DISTINCT mediaId FROM face)
        ORDER BY m.id
        """
    )
    suspend fun localPhotoIdsWithoutFaces(): List<Long>

    @Query(
        """
        SELECT m.id FROM media m
        WHERE m.storageType = 'LOCAL' AND m.video IS NULL
        ORDER BY m.id
        """
    )
    suspend fun allLocalPhotoIds(): List<Long>
}
