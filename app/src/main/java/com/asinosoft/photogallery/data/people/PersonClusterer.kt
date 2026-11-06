package com.asinosoft.photogallery.data.people

import android.util.Log
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Кластеризация лиц: nearest-centroid (без single-linkage цепочек A≈B≈C→все в одном Person).
 * Метрика — euclidean по L2-нормированному MobileFaceNet 192-d.
 * Сид бакета один раз заменяется средним ядра, затем лица перекладываются.
 */
@Singleton
class PersonClusterer @Inject constructor(
    private val personDao: PersonDao,
    private val peopleAlbums: PeopleAlbums,
) {
    suspend fun recluster(threshold: Float = SIMILARITY_THRESHOLD) {
        val faces = personDao.getAllFaces().filter { isClusterableFace(it) }
        if (faces.isEmpty()) {
            peopleAlbums.syncAll()
            return
        }

        val usable =
            faces.filter { FaceEmbedding.fromBytes(it.embedding).size == FaceEmbedding.DIM }
        if (usable.isNotEmpty()) {
            clusterByCentroids(usable, threshold)
        }

        // Merge отключён: даже pairwise на усреднённых центроидах снова склеивал всех.
        pruneEmptyPeople()
        ensureCoverFaces()
        peopleAlbums.syncAll()
    }

    /**
     * Проход 1: лицо садится на ближайший замороженный центроид, иначе новый Person.
     * Затем сид заменяется L2-средним ближайших 70% лиц. Проход 2 только перекладывает
     * безымянные лица на обновлённые центроиды и новых людей не создаёт.
     */
    private suspend fun clusterByCentroids(faces: List<Face>, threshold: Float) {
        data class Bucket(
            val personId: Long,
            var centroid: FloatArray,
            val faceIds: MutableList<Long>,
            val named: Boolean,
        )

        val vectors = HashMap<Long, FloatArray>(faces.size)
        for (face in faces) {
            val vector = FaceEmbedding.fromBytes(face.embedding)
            FaceEmbedding.normalizeInPlace(vector)
            vectors[face.id] = vector
        }

        val people = personDao.getAllPeople().associateBy { it.id }
        val buckets = mutableListOf<Bucket>()

        fun nearest(emb: FloatArray): Pair<Int, Float> {
            var bestIdx = -1
            var bestDist = Float.MAX_VALUE
            for (i in buckets.indices) {
                val distance = FaceEmbedding.euclideanDistance(emb, buckets[i].centroid)
                if (distance < bestDist) {
                    bestDist = distance
                    bestIdx = i
                }
            }
            return bestIdx to bestDist
        }

        // Сначала именованные — якоря, чтобы имена не потерялись. Сид сразу ядро, не всё среднее.
        val namedFaces = faces.filter { face ->
            val pid = face.personId ?: return@filter false
            !people[pid]?.displayName.isNullOrBlank()
        }.groupBy { it.personId!! }

        for ((personId, group) in namedFaces) {
            val memberVectors = group.map { vectors.getValue(it.id) }
            val mean = average(memberVectors)
            FaceEmbedding.normalizeInPlace(mean)
            buckets += Bucket(
                personId = personId,
                centroid = FaceEmbedding.coreAverage(memberVectors, mean),
                faceIds = group.map { it.id }.toMutableList(),
                named = true,
            )
        }

        val namedFaceIds = namedFaces.values.flatten().map { it.id }.toSet()
        // Стабильный порядок: крупные кропы раньше (чуть надёжнее как сиды)
        val rest = faces
            .filter { it.id !in namedFaceIds }
            .sortedByDescending { (it.right - it.left) * (it.bottom - it.top) }

        val assignment = HashMap<Long, Int>(rest.size)
        var created = 0
        for (face in rest) {
            val emb = vectors.getValue(face.id)
            val (bestIdx, bestDist) = nearest(emb)
            if (bestIdx >= 0 && bestDist <= threshold) {
                buckets[bestIdx].faceIds += face.id
                assignment[face.id] = bestIdx
                // Центроид заморожен на сиде — любой online-update тянет цепочку A→B→C.
            } else {
                val id = personDao.insertPerson(Person())
                buckets += Bucket(
                    personId = id,
                    centroid = emb.copyOf(),
                    faceIds = mutableListOf(face.id),
                    named = false,
                )
                assignment[face.id] = buckets.lastIndex
                created++
            }
        }

        for (bucket in buckets) {
            val memberVectors = bucket.faceIds.map { vectors.getValue(it) }
            if (memberVectors.isEmpty()) continue
            bucket.centroid = FaceEmbedding.coreAverage(memberVectors, bucket.centroid)
        }

        var moved = 0
        for (face in rest) {
            val current = assignment[face.id] ?: continue
            val emb = vectors.getValue(face.id)
            val (bestIdx, bestDist) = nearest(emb)
            if (bestIdx < 0 || bestIdx == current || bestDist > threshold) continue
            buckets[current].faceIds.remove(face.id)
            buckets[bestIdx].faceIds += face.id
            assignment[face.id] = bestIdx
            moved++
        }

        for (b in buckets) {
            if (b.faceIds.isNotEmpty()) {
                personDao.assignFacesToPerson(b.faceIds, b.personId)
            }
        }
        Log.i(
            TAG,
            "cluster centroids: buckets=${buckets.size} created=$created moved=$moved " +
                    "faces=${faces.size} threshold=$threshold",
        )
    }

    private suspend fun pruneEmptyPeople() {
        for (person in personDao.getAllPeople()) {
            val faces = personDao.getFacesForPerson(person.id).filter { isClusterableFace(it) }
            if (faces.isEmpty() && person.displayName.isNullOrBlank()) {
                peopleAlbums.deletePersonAlbum(person)
            }
        }
    }

    private suspend fun ensureCoverFaces() {
        for (person in personDao.getAllPeople()) {
            val faces = personDao.getFacesForPerson(person.id)
                .filter { isClusterableFace(it) && !it.thumbPath.isNullOrBlank() }
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
        return out
    }

    companion object {
        private const val TAG = "PersonClusterer"

        /**
         * Euclidean на L2-нормированном MobileFaceNet.
         * ~1.0 — склейка ракурсов одного человека; выше — снова мешок.
         */
        const val SIMILARITY_THRESHOLD = 1.00f

        /** Доля кадра: мельче — часто ложные детекты (текстуры/фон), портят кластеры. */
        private const val MIN_FACE_WIDTH = 0.08f
        private const val MIN_FACE_AREA = 0.008f

        fun isClusterableFace(face: Face): Boolean {
            val w = face.right - face.left
            val h = face.bottom - face.top
            if (w < MIN_FACE_WIDTH || h < MIN_FACE_WIDTH) return false
            if (w * h < MIN_FACE_AREA) return false
            return true
        }
    }
}
