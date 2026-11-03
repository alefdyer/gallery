package com.asinosoft.photogallery.data.people

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.graphics.Rect
import android.os.Build
import android.util.Log
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.MediaDao
import com.asinosoft.photogallery.data.storage.StorageType
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Фоновая очередь детекта лиц на локальных фото после скана / backfill.
 */
@Singleton
class PeopleIndexQueue @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val mediaDao: MediaDao,
    private val personDao: PersonDao,
    private val peopleAlbums: PeopleAlbums,
    private val clusterer: PersonClusterer,
    private val faceEmbedder: FaceEmbedder,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<Long>(Channel.UNLIMITED)
    private val started = AtomicBoolean(false)
    private val enqueued = ConcurrentHashMap.newKeySet<Long>()
    private val pending = AtomicInteger(0)
    private val processedSinceCluster = AtomicInteger(0)
    private val progressDone = AtomicInteger(0)
    private val progressTotal = AtomicInteger(0)

    private val _indexing = MutableStateFlow(false)
    val indexing: StateFlow<Boolean> = _indexing.asStateFlow()

    private val _progress = MutableStateFlow(PeopleIndexProgress())
    val progress: StateFlow<PeopleIndexProgress> = _progress.asStateFlow()

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.12f)
            .build()
        FaceDetection.getClient(options)
    }

    fun enqueue(mediaId: Long) {
        if (!enqueued.add(mediaId)) return
        pending.incrementAndGet()
        bumpTotal(1)
        _indexing.value = true
        ensureStarted()
        if (!channel.trySend(mediaId).isSuccess) {
            enqueued.remove(mediaId)
            if (pending.decrementAndGet() <= 0) finishIndexing()
        }
    }

    fun enqueueAll(mediaIds: Collection<Long>) {
        mediaIds.forEach(::enqueue)
    }

    /** Доиндексация + re-embed при смене версии эмбеддера / чужой размерности. */
    fun startBackfill() {
        scope.launch {
            peopleAlbums.ensureCategory()
            _indexing.value = true
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val embedVer = prefs.getInt(KEY_EMBED_VERSION, 0)
            val needReclusterReset =
                prefs.getInt(KEY_CLUSTER_VERSION, 0) != CLUSTER_VERSION
            val allFaces = personDao.getRealFaces()
            // Re-encode только векторы чужой размерности. Смена CLUSTER_VERSION —
            // только сброс Person + recluster, без часового refresh.
            val facesToRefresh = allFaces.filter {
                FaceEmbedding.fromBytes(it.embedding).size != FaceEmbedding.DIM
            }
            val ids = personDao.localPhotoIdsWithoutFaces()
            resetProgress(facesToRefresh.size + ids.size)
            Log.i(
                TAG,
                "backfill refresh=${facesToRefresh.size}/${allFaces.size} " +
                        "photos=${ids.size} reclusterReset=$needReclusterReset",
            )
            runCatching { refreshEmbeddingsAndThumbs(facesToRefresh) }
                .onFailure { Log.w(TAG, "refresh embeddings failed", it) }
            if (embedVer != FaceEmbedder.EMBEDDING_VERSION) {
                prefs.edit { putInt(KEY_EMBED_VERSION, FaceEmbedder.EMBEDDING_VERSION) }
            }
            if (needReclusterReset) {
                runCatching {
                    val oldPeople = personDao.getAllPeople()
                    personDao.clearAllFacePersonIds()
                    oldPeople.forEach { peopleAlbums.deletePersonAlbum(it) }
                    personDao.deleteAllPeople()
                }.onFailure { Log.w(TAG, "reset people failed", it) }
                prefs.edit { putInt(KEY_CLUSTER_VERSION, CLUSTER_VERSION) }
                // Сразу пересобрать из уже готовых эмбеддингов
                runCatching { clusterer.recluster() }
                    .onFailure { Log.w(TAG, "recluster failed", it) }
            }
            if (ids.isEmpty()) {
                if (!needReclusterReset) {
                    runCatching { clusterer.recluster() }
                        .onFailure { Log.w(TAG, "recluster failed", it) }
                }
                finishIndexing()
            } else {
                enqueueAllWithoutBumpingTotal(ids)
            }
        }
    }

    private fun enqueueAllWithoutBumpingTotal(mediaIds: Collection<Long>) {
        for (mediaId in mediaIds) {
            if (!enqueued.add(mediaId)) continue
            pending.incrementAndGet()
            _indexing.value = true
            ensureStarted()
            if (!channel.trySend(mediaId).isSuccess) {
                enqueued.remove(mediaId)
                if (pending.decrementAndGet() <= 0) finishIndexing()
            }
        }
        publishProgress()
    }

    private suspend fun refreshEmbeddingsAndThumbs(faces: List<Face>) {
        if (faces.isEmpty()) return
        Log.i(TAG, "refresh ${faces.size} face embeddings/thumbs")
        val dir = faceThumbDir()
        for ((mediaId, mediaFaces) in faces.groupBy { it.mediaId }) {
            val media = mediaDao.getById(mediaId)
            val bitmap = media?.let { decodeBitmap(it) }
            if (bitmap == null) {
                repeat(mediaFaces.size) { tickProgress() }
                continue
            }
            try {
                for (face in mediaFaces) {
                    val box = Rect(
                        (face.left * bitmap.width).roundToInt(),
                        (face.top * bitmap.height).roundToInt(),
                        (face.right * bitmap.width).roundToInt(),
                        (face.bottom * bitmap.height).roundToInt(),
                    )
                    if (box.width() >= 8 && box.height() >= 8) {
                        val emb = faceEmbedder.encode(bitmap, box)
                        val oldThumb = face.thumbPath
                        val thumb = saveFaceThumb(bitmap, box, dir)
                        personDao.updateFace(
                            face.copy(
                                embedding = FaceEmbedding.toBytes(emb),
                                thumbPath = thumb?.absolutePath ?: face.thumbPath,
                            )
                        )
                        if (oldThumb != null && oldThumb != thumb?.absolutePath) {
                            runCatching { File(oldThumb).delete() }
                        }
                    }
                    tickProgress()
                }
            } finally {
                if (!bitmap.isRecycled) bitmap.recycle()
            }
        }
    }

    private fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            for (mediaId in channel) {
                try {
                    process(mediaId)
                } catch (e: Exception) {
                    Log.w(TAG, "index failed $mediaId", e)
                } finally {
                    enqueued.remove(mediaId)
                    tickProgress()
                    if (pending.decrementAndGet() <= 0) {
                        runCatching { clusterer.recluster() }
                            .onFailure { Log.w(TAG, "recluster failed", it) }
                        processedSinceCluster.set(0)
                        finishIndexing()
                    } else if (processedSinceCluster.incrementAndGet() >= CLUSTER_EVERY) {
                        processedSinceCluster.set(0)
                        // Промежуточный recluster (centroid, не O(n²) union-find) — чтобы UI
                        // не держал одного «мусорного» Person до конца индексации.
                        runCatching { clusterer.recluster() }
                            .onFailure { Log.w(TAG, "recluster failed", it) }
                    }
                }
                delay(JOB_GAP_MS)
            }
        }
    }

    private suspend fun process(mediaId: Long) {
        if (personDao.faceCountForMedia(mediaId) > 0) return
        val media = mediaDao.getById(mediaId) ?: return
        if (media.storageType != StorageType.LOCAL || media.video != null) return

        val bitmap = decodeBitmap(media) ?: return
        try {
            val scaled = scaleForDetect(bitmap)
            val image = InputImage.fromBitmap(scaled, 0)
            val faces = detector.process(image).await()
            if (faces.isEmpty()) {
                insertEmptyMarker(mediaId)
                return
            }

            val scaleX = bitmap.width.toFloat() / scaled.width
            val scaleY = bitmap.height.toFloat() / scaled.height
            val dir = faceThumbDir()
            val rows = faces.mapNotNull { mlFace ->
                val box = mlFace.boundingBox
                val fullBox = Rect(
                    (box.left * scaleX).roundToInt(),
                    (box.top * scaleY).roundToInt(),
                    (box.right * scaleX).roundToInt(),
                    (box.bottom * scaleY).roundToInt(),
                )
                val left = fullBox.left / bitmap.width.toFloat()
                val top = fullBox.top / bitmap.height.toFloat()
                val right = fullBox.right / bitmap.width.toFloat()
                val bottom = fullBox.bottom / bitmap.height.toFloat()
                val candidate = Face(
                    mediaId = mediaId,
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    embedding = FaceEmbedding.toBytes(FloatArray(FaceEmbedding.DIM)),
                    thumbPath = null,
                )
                // Мелкие ложные детекты не кладём в кластеризацию/альбомы.
                if (!PersonClusterer.isClusterableFace(candidate)) return@mapNotNull null
                if (fullBox.width() < 32 || fullBox.height() < 32) return@mapNotNull null

                val emb = faceEmbedder.encode(bitmap, fullBox)
                // Нулевой/битый вектор → все «пустые» лица схлопывались в одного Person.
                var mag = 0f
                for (v in emb) mag += v * v
                if (mag < 1e-4f) return@mapNotNull null

                candidate.copy(
                    embedding = FaceEmbedding.toBytes(emb),
                    thumbPath = saveFaceThumb(bitmap, fullBox, dir)?.absolutePath,
                )
            }
            if (rows.isNotEmpty()) {
                personDao.insertFaces(rows)
            } else {
                insertEmptyMarker(mediaId)
            }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    private suspend fun insertEmptyMarker(mediaId: Long) {
        personDao.insertFace(
            Face(
                mediaId = mediaId,
                left = 0f,
                top = 0f,
                right = 0f,
                bottom = 0f,
                embedding = FaceEmbedding.toBytes(FloatArray(FaceEmbedding.DIM)),
                thumbPath = null,
            )
        )
    }

    private fun decodeBitmap(media: Media): Bitmap? {
        val uri = media.uri ?: return null
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                ImageDecoder.decodeBitmap(source) { decoder, _, _ ->
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    decoder.isMutableRequired = false
                }
            } else {
                context.contentResolver.openInputStream(uri)?.use {
                    BitmapFactory.decodeStream(it)
                }
            }
        }.onFailure {
            Log.w(TAG, "decode failed ${media.id}", it)
        }.getOrNull()
    }

    private fun scaleForDetect(bitmap: Bitmap): Bitmap {
        val maxSide = max(bitmap.width, bitmap.height)
        if (maxSide <= MAX_DETECT_SIDE) return bitmap
        val scale = MAX_DETECT_SIDE.toFloat() / maxSide
        val w = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val h = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, w, h, true)
    }

    private fun faceThumbDir(): File =
        File(context.filesDir, "face_thumbs").also { it.mkdirs() }

    private fun saveFaceThumb(bitmap: Bitmap, box: Rect, dir: File): File? {
        val square = FaceEmbedding.expandSquare(box, bitmap.width, bitmap.height, 0.45f)
        val left = square.left.coerceIn(0, bitmap.width - 1)
        val top = square.top.coerceIn(0, bitmap.height - 1)
        val width = (square.right - left).coerceAtMost(bitmap.width - left).coerceAtLeast(1)
        val height = (square.bottom - top).coerceAtMost(bitmap.height - top).coerceAtLeast(1)
        return runCatching {
            val crop = Bitmap.createBitmap(bitmap, left, top, width, height)
            val size = 320
            val thumb = Bitmap.createScaledBitmap(crop, size, size, true)
            if (crop !== thumb && !crop.isRecycled) crop.recycle()
            val file = File(dir, "f_${System.currentTimeMillis()}_${left}_$top.jpg")
            FileOutputStream(file).use { out ->
                thumb.compress(Bitmap.CompressFormat.JPEG, 88, out)
            }
            if (!thumb.isRecycled) thumb.recycle()
            file
        }.getOrNull()
    }

    private fun resetProgress(total: Int) {
        progressDone.set(0)
        progressTotal.set(total.coerceAtLeast(0))
        publishProgress()
    }

    private fun bumpTotal(by: Int) {
        if (by <= 0) return
        // Новая сессия, если предыдущая завершена
        if (progressTotal.get() > 0 && progressDone.get() >= progressTotal.get() && pending.get() <= 1) {
            progressDone.set(0)
            progressTotal.set(by)
        } else {
            progressTotal.addAndGet(by)
        }
        publishProgress()
    }

    private fun tickProgress() {
        progressDone.incrementAndGet()
        publishProgress()
    }

    private fun publishProgress() {
        _progress.value = PeopleIndexProgress(
            done = progressDone.get().coerceAtLeast(0),
            total = progressTotal.get().coerceAtLeast(0),
        )
    }

    private fun finishIndexing() {
        _indexing.value = false
        val total = progressTotal.get()
        if (total > 0) {
            progressDone.set(total)
            publishProgress()
        }
        // Сброс после короткой паузы не нужен — UI скрывает при !active (done >= total)
        // Но для следующей сессии bumpTotal сам сбросит.
    }

    companion object {
        private const val TAG = "PeopleIndex"
        private const val PREFS = "people_index"
        private const val KEY_EMBED_VERSION = "embedder_version"
        private const val KEY_CLUSTER_VERSION = "cluster_version"

        /** Бамп сбрасывает Person и пересобирает кластеры без re-embed. */
        private const val CLUSTER_VERSION = 8
        private const val JOB_GAP_MS = 40L

        /** Промежуточный centroid-recluster для UI. */
        private const val CLUSTER_EVERY = 200
        private const val MAX_DETECT_SIDE = 1280
    }
}
