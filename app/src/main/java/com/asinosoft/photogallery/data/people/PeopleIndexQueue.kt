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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<Long>(Channel.UNLIMITED)
    private val started = AtomicBoolean(false)
    private val enqueued = ConcurrentHashMap.newKeySet<Long>()
    private val pending = AtomicInteger(0)
    private val processedSinceCluster = AtomicInteger(0)

    private val _indexing = MutableStateFlow(false)
    val indexing: StateFlow<Boolean> = _indexing.asStateFlow()

    private val detector by lazy {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setMinFaceSize(0.10f)
            .build()
        FaceDetection.getClient(options)
    }

    fun enqueue(mediaId: Long) {
        if (!enqueued.add(mediaId)) return
        pending.incrementAndGet()
        _indexing.value = true
        ensureStarted()
        if (!channel.trySend(mediaId).isSuccess) {
            enqueued.remove(mediaId)
            if (pending.decrementAndGet() <= 0) _indexing.value = false
        }
    }

    fun enqueueAll(mediaIds: Collection<Long>) {
        mediaIds.forEach(::enqueue)
    }

    /** Доиндексация + обновление эмбеддингов/портретных кропов для уже найденных лиц. */
    fun startBackfill() {
        scope.launch {
            peopleAlbums.ensureCategory()
            _indexing.value = true
            runCatching { refreshEmbeddingsAndThumbs() }
                .onFailure { Log.w(TAG, "refresh embeddings failed", it) }
            val ids = personDao.localPhotoIdsWithoutFaces()
            Log.i(TAG, "backfill ${ids.size} local photos")
            if (ids.isEmpty()) {
                runCatching { clusterer.recluster() }
                    .onFailure { Log.w(TAG, "recluster failed", it) }
                _indexing.value = false
            } else {
                enqueueAll(ids)
            }
        }
    }

    private suspend fun refreshEmbeddingsAndThumbs() {
        val faces = personDao.getRealFaces()
        if (faces.isEmpty()) return
        Log.i(TAG, "refresh ${faces.size} face embeddings/thumbs")
        val dir = faceThumbDir()
        for ((mediaId, mediaFaces) in faces.groupBy { it.mediaId }) {
            val media = mediaDao.getById(mediaId) ?: continue
            val bitmap = decodeBitmap(media) ?: continue
            try {
                for (face in mediaFaces) {
                    val box = Rect(
                        (face.left * bitmap.width).roundToInt(),
                        (face.top * bitmap.height).roundToInt(),
                        (face.right * bitmap.width).roundToInt(),
                        (face.bottom * bitmap.height).roundToInt(),
                    )
                    if (box.width() < 8 || box.height() < 8) continue
                    val emb = FaceEmbedding.encode(bitmap, box)
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
                    if (pending.decrementAndGet() <= 0) {
                        _indexing.value = false
                        runCatching { clusterer.recluster() }
                            .onFailure { Log.w(TAG, "recluster failed", it) }
                        processedSinceCluster.set(0)
                    } else if (processedSinceCluster.incrementAndGet() >= CLUSTER_EVERY) {
                        processedSinceCluster.set(0)
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
                if (fullBox.width() < 8 || fullBox.height() < 8) return@mapNotNull null

                val emb = FaceEmbedding.encode(bitmap, fullBox)
                val thumb = saveFaceThumb(bitmap, fullBox, dir)
                Face(
                    mediaId = mediaId,
                    left = fullBox.left / bitmap.width.toFloat(),
                    top = fullBox.top / bitmap.height.toFloat(),
                    right = fullBox.right / bitmap.width.toFloat(),
                    bottom = fullBox.bottom / bitmap.height.toFloat(),
                    embedding = FaceEmbedding.toBytes(emb),
                    thumbPath = thumb?.absolutePath,
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

    companion object {
        private const val TAG = "PeopleIndex"
        private const val JOB_GAP_MS = 40L
        private const val CLUSTER_EVERY = 40
        private const val MAX_DETECT_SIDE = 1280
    }
}
