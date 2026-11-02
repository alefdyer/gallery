package com.asinosoft.photogallery.data.people

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Полная перекластеризация лиц (union-find по cosine) + слияние похожих Person.
 * Инкрементальный «только unassigned → новый Person» плодил дубликаты одного человека.
 */
@Singleton
class PersonClusterer @Inject constructor(
    private val personDao: PersonDao,
    private val peopleAlbums: PeopleAlbums,
) {
    suspend fun recluster(threshold: Float = SIMILARITY_THRESHOLD) {
        val faces = personDao.getAllFaces().filter { it.right - it.left > 0.01f }
        if (faces.isEmpty()) {
            peopleAlbums.syncAll()
            return
        }

        // Старые эмбеддинги другой размерности (landmarks) — не сравниваем, оставим как есть
        // до переиндексации; для них сработает merge по уже назначенным personId.
        val usable =
            faces.filter { FaceEmbedding.fromBytes(it.embedding).size == FaceEmbedding.DIM }
        if (usable.size >= 2) {
            clusterBySimilarity(usable, threshold)
        }

        mergeSimilarPeople(threshold)
        ensureCoverFaces()
        peopleAlbums.syncAll()
    }

    private suspend fun clusterBySimilarity(faces: List<Face>, threshold: Float) {
        val n = faces.size
        val emb = faces.map { FaceEmbedding.fromBytes(it.embedding) }
        val parent = IntArray(n) { it }
        val rank = IntArray(n)

        fun find(i: Int): Int {
            var x = i
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]
                x = parent[x]
            }
            return x
        }

        fun union(a: Int, b: Int) {
            var ra = find(a)
            var rb = find(b)
            if (ra == rb) return
            if (rank[ra] < rank[rb]) {
                val t = ra
                ra = rb
                rb = t
            }
            parent[rb] = ra
            if (rank[ra] == rank[rb]) rank[ra]++
        }

        for (i in 0 until n) {
            for (j in i + 1 until n) {
                if (FaceEmbedding.cosineSimilarity(emb[i], emb[j]) >= threshold) {
                    union(i, j)
                }
            }
        }

        val clusters = (0 until n).groupBy { find(it) }.values
        val people = personDao.getAllPeople().associateBy { it.id }

        for (indices in clusters) {
            val clusterFaces = indices.map { faces[it] }
            val votes = clusterFaces.mapNotNull { it.personId }
                .groupingBy { it }
                .eachCount()

            val namedCandidates = votes.keys.mapNotNull { people[it] }
                .filter { !it.displayName.isNullOrBlank() }
            val targetId = namedCandidates.maxByOrNull { votes[it.id] ?: 0 }?.id
                ?: votes.maxByOrNull { it.value }?.key
                ?: personDao.insertPerson(Person())

            personDao.assignFacesToPerson(clusterFaces.map { it.id }, targetId)

            // Метаданные с «проигравших» людей
            val target = personDao.getPerson(targetId) ?: continue
            var merged = target
            for (otherId in votes.keys) {
                if (otherId == targetId) continue
                val other = people[otherId] ?: continue
                merged = merged.copy(
                    displayName = merged.displayName ?: other.displayName,
                    relationship = merged.relationship ?: other.relationship,
                    contactUri = merged.contactUri ?: other.contactUri,
                    contactId = merged.contactId ?: other.contactId,
                    coverFaceId = merged.coverFaceId ?: other.coverFaceId,
                )
            }
            if (merged != target) {
                personDao.updatePerson(merged.copy(updatedAt = System.currentTimeMillis()))
            }
        }
    }

    /** Склеивает Person, чьи центроиды всё ещё близки (после инкрементальных проходов). */
    private suspend fun mergeSimilarPeople(threshold: Float) {
        val people = personDao.getAllPeople()
        if (people.size < 2) return

        data class Centroid(val personId: Long, val vector: FloatArray, val named: Boolean)

        val centroids = people.mapNotNull { person ->
            val vectors = personDao.getFacesForPerson(person.id)
                .filter { it.right - it.left > 0.01f }
                .map { FaceEmbedding.fromBytes(it.embedding) }
                .filter { it.size == FaceEmbedding.DIM }
            if (vectors.isEmpty()) return@mapNotNull null
            Centroid(
                personId = person.id,
                vector = average(vectors),
                named = !person.displayName.isNullOrBlank(),
            )
        }
        if (centroids.size < 2) return

        val parent =
            centroids.indices.associate { centroids[it].personId to centroids[it].personId }
                .toMutableMap()

        fun find(id: Long): Long {
            var x = id
            while (parent[x] != x) {
                parent[x] = parent[parent[x]]!!
                x = parent[x]!!
            }
            return x
        }

        for (i in centroids.indices) {
            for (j in i + 1 until centroids.size) {
                val sim = FaceEmbedding.cosineSimilarity(centroids[i].vector, centroids[j].vector)
                if (sim < threshold) continue
                val a = centroids[i]
                val b = centroids[j]
                val ra = find(a.personId)
                val rb = find(b.personId)
                if (ra == rb) continue
                // Именованный побеждает
                val aNamed = people.firstOrNull { it.id == ra }?.displayName.isNullOrBlank().not()
                val bNamed = people.firstOrNull { it.id == rb }?.displayName.isNullOrBlank().not()
                when {
                    aNamed && !bNamed -> parent[rb] = ra
                    bNamed && !aNamed -> parent[ra] = rb
                    else -> parent[maxOf(ra, rb)] = minOf(ra, rb)
                }
            }
        }

        val groups = centroids.map { it.personId }.groupBy { find(it) }
        for ((root, members) in groups) {
            if (members.size < 2) continue
            val target = members.minOrNull() ?: continue
            for (source in members) {
                if (source == target) continue
                val src = personDao.getPerson(source) ?: continue
                val dst = personDao.getPerson(target) ?: continue
                personDao.moveFaces(source, target)
                personDao.updatePerson(
                    dst.copy(
                        displayName = dst.displayName ?: src.displayName,
                        relationship = dst.relationship ?: src.relationship,
                        contactUri = dst.contactUri ?: src.contactUri,
                        contactId = dst.contactId ?: src.contactId,
                        coverFaceId = dst.coverFaceId ?: src.coverFaceId,
                        updatedAt = System.currentTimeMillis(),
                    )
                )
                peopleAlbums.deletePersonAlbum(src)
            }
        }
    }

    private suspend fun ensureCoverFaces() {
        for (person in personDao.getAllPeople()) {
            val faces = personDao.getFacesForPerson(person.id)
                .filter { it.right - it.left > 0.01f && !it.thumbPath.isNullOrBlank() }
            if (faces.isEmpty()) continue
            val coverOk = person.coverFaceId?.let { id -> faces.any { it.id == id } } == true
            if (coverOk) continue
            personDao.updatePerson(
                person.copy(
                    coverFaceId = faces.first().id,
                    updatedAt = System.currentTimeMillis(),
                )
            )
        }
    }

    private fun average(vectors: List<FloatArray>): FloatArray {
        val dim = vectors.first().size
        val out = FloatArray(dim)
        for (v in vectors) {
            for (i in 0 until dim) out[i] += v[i]
        }
        val n = vectors.size.toFloat()
        for (i in 0 until dim) out[i] /= n
        var sum = 0f
        for (x in out) sum += x * x
        val norm = kotlin.math.sqrt(sum)
        if (norm > 1e-6f) for (i in out.indices) out[i] /= norm
        return out
    }

    companion object {
        /** Ниже прежнего 0.72: патч-эмбеддинг иначе оставлял одного человека в куче Person. */
        const val SIMILARITY_THRESHOLD = 0.55f
    }
}
