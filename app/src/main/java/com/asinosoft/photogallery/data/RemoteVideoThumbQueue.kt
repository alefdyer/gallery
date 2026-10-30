package com.asinosoft.photogallery.data

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.os.Build
import android.os.SystemClock
import android.util.Log
import com.asinosoft.photogallery.GalleryApp
import com.asinosoft.photogallery.data.storage.StorageProviderRegistry
import com.asinosoft.photogallery.data.storage.StorageService
import com.asinosoft.photogallery.data.storage.StorageType
import com.asinosoft.photogallery.data.storage.gdrive.GoogleDriveStorageProvider
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.Request
import kotlin.math.roundToInt

/**
 * Фоновая очередь remote: превью/длительность видео и EXIF takenAt для фото (WebDAV).
 * Один job за раз; auth обязателен. После ошибки — cooldown (OOM/FD).
 */
@Singleton
class RemoteVideoThumbQueue @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val storageProviderRegistry: StorageProviderRegistry,
    /** Lazy: MediaService → Queue → StorageService → MediaService. */
    private val storageService: dagger.Lazy<StorageService>,
    private val mediaDao: MediaDao,
) {
    data class Job(
        val mediaId: Long,
        val storageId: Long,
        val storageItemId: String,
        val size: Long,
        val uri: String? = null,
        val needThumb: Boolean = true,
        val needDuration: Boolean = false,
        /** WebDAV/Nextcloud: EXIF DateTimeOriginal (Range), если нет takenAt. */
        val needTakenAt: Boolean = false,
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val channel = Channel<Job>(Channel.UNLIMITED)
    private val started = AtomicBoolean(false)
    private val enqueued = ConcurrentHashMap.newKeySet<Long>()

    /** mediaId → elapsedRealtime до которого не ставить в очередь снова. */
    private val cooldownUntil = ConcurrentHashMap<Long, Long>()
    private val gson = Gson()

    fun enqueue(job: Job) {
        if (!job.needThumb && !job.needDuration && !job.needTakenAt) return
        val until = cooldownUntil[job.mediaId] ?: 0L
        if (SystemClock.elapsedRealtime() < until) return
        if (!enqueued.add(job.mediaId)) return
        ensureStarted()
        if (!channel.trySend(job).isSuccess) {
            enqueued.remove(job.mediaId)
        }
    }

    private fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            for (job in channel) {
                try {
                    runCatching { process(job) }
                        .onFailure {
                            Log.w(TAG, "job failed ${job.mediaId}", it)
                            markCooldown(job.mediaId, FAIL_COOLDOWN_MS)
                        }
                } finally {
                    enqueued.remove(job.mediaId)
                }
                delay(JOB_GAP_MS)
            }
        }
    }

    private fun markCooldown(mediaId: Long, ms: Long) {
        cooldownUntil[mediaId] = SystemClock.elapsedRealtime() + ms
    }

    private suspend fun process(job: Job) {
        val thumbFile = ThumbnailCache.getFile(context, job.mediaId)
        val hasThumb = thumbFile.exists() && thumbFile.length() > 0
        val needThumb = job.needThumb && !hasThumb
        val existing = mediaDao.getById(job.mediaId)
        var needDuration = job.needDuration &&
                (existing?.video?.duration ?: 0L) <= 0L &&
                existing?.video != null
        val image = existing?.image
        val needImageMeta = job.needTakenAt &&
                image != null &&
                (positiveEpoch(existing.takenAt) == null || image.width <= 0 || image.height <= 0)
        if (!needThumb && !needDuration && !needImageMeta) return

        val storage = storageService.get()
        if (storage.ensureFreshAuth(job.storageId) == null) {
            markCooldown(job.mediaId, AUTH_COOLDOWN_MS)
            return
        }
        val headers = storage.httpAuthHeaders(job.storageId)
        val storageType = existing?.storageType
        val filename = existing?.filename.orEmpty()
        val mime = existing?.mimeType.orEmpty()
        val size = existing?.size?.takeIf { it > 0 } ?: job.size

        if (needImageMeta) {
            val fromName = parseTakenAtFromFilename(filename)
            val url = job.uri?.takeIf { it.startsWith("http") }
                ?: runCatching {
                    storageProviderRegistry.getStorageProvider(job.storageId)
                        .getMediaUri(
                            Media(
                                id = job.mediaId,
                                storageId = job.storageId,
                                storageItemId = job.storageItemId,
                                size = size,
                                image = Image(),
                            )
                        ).toString()
                }.getOrNull()
            val probe = if (url != null &&
                (url.startsWith("http://") || url.startsWith("https://"))
            ) {
                val app = context.applicationContext as? GalleryApp
                app?.httpClient?.let { ExifTakenAt.probeHttp(it, url, headers) } ?: ExifProbe()
            } else {
                ExifProbe()
            }
            val taken = positiveEpoch(existing.takenAt) ?: probe.takenAt ?: fromName
            val width = image.width.takeIf { it > 0 } ?: probe.width
            val height = image.height.takeIf { it > 0 } ?: probe.height
            if (taken != null || width > 0) {
                saveImageMeta(
                    mediaId = job.mediaId,
                    takenAt = taken,
                    width = width,
                    height = height,
                    orientation = probe.orientation.takeIf { it != 0 } ?: image.orientation,
                )
                Log.i(
                    TAG,
                    "Image meta media=${job.mediaId} taken=$taken ${width}x$height"
                )
            } else {
                markCooldown(job.mediaId, FAIL_COOLDOWN_MS)
            }
        }

        // Drive: files.get. У телефонных .mp4/.mkv videoMediaMetadata часто пустой
        // (в «Олеся» 710 mp4 с duration=0, 3gp — ок). MMR на сотни файлов → OOM/FD,
        // поэтому сразу оценка по размеру; точная длительность допишется при открытии.
        if (needDuration && storageType == StorageType.GOOGLE_DRIVE) {
            val apiDuration = fetchDriveDurationMs(
                fileId = job.storageItemId,
                headers = headers,
                filename = filename,
                mimeType = mime,
                sizeHint = size,
            )
            if (apiDuration > 0L) {
                saveDuration(job.mediaId, apiDuration)
                Log.i(TAG, "Drive API duration media=${job.mediaId} → ${apiDuration}ms")
            } else {
                estimateAndSave(job.mediaId, size, filename, mime)
            }
            needDuration = false
        }

        if (!needThumb && !needDuration) return

        // MMR / стрим: не качаем гигантов целиком (mkv 2+ GiB).
        if (size > MAX_SIZE_BYTES) {
            if (needDuration) {
                estimateAndSave(job.mediaId, size, filename, mime)
            }
            markCooldown(job.mediaId, FAIL_COOLDOWN_MS)
            return
        }

        val url = job.uri?.takeIf { it.startsWith("http") }
            ?: run {
                val provider = storageProviderRegistry.getStorageProvider(job.storageId)
                runCatching {
                    provider.getMediaUri(
                        Media(
                            id = job.mediaId,
                            storageId = job.storageId,
                            storageItemId = job.storageItemId,
                            size = size,
                            video = Video(duration = 0),
                        )
                    ).toString()
                }.getOrNull()
            }
            ?: run {
                if (needDuration) estimateAndSave(job.mediaId, size, filename, mime)
                markCooldown(job.mediaId, FAIL_COOLDOWN_MS)
                return
            }

        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            if (needDuration) estimateAndSave(job.mediaId, size, filename, mime)
            markCooldown(job.mediaId, FAIL_COOLDOWN_MS)
            return
        }
        if (!supportsRange(url, headers)) {
            if (needDuration) estimateAndSave(job.mediaId, size, filename, mime)
            markCooldown(job.mediaId, FAIL_COOLDOWN_MS)
            return
        }

        val probed = probe(url, headers, needThumb = needThumb, needDuration = needDuration)
        if (probed == null) {
            if (needDuration) estimateAndSave(job.mediaId, size, filename, mime)
            markCooldown(job.mediaId, FAIL_COOLDOWN_MS)
            return
        }
        probed.bitmap?.let { bitmap ->
            ThumbnailCache.saveBitmap(context, job.mediaId, bitmap)
            bitmap.recycle()
        }
        if (needDuration && probed.durationMs > 0L) {
            saveDuration(job.mediaId, probed.durationMs)
        } else if (needDuration) {
            estimateAndSave(job.mediaId, size, filename, mime)
            markCooldown(job.mediaId, FAIL_COOLDOWN_MS)
        }
    }

    /** Грубая оценка для сетки, если Drive/MMR не дали duration (лучше чем пусто). */
    private suspend fun estimateAndSave(
        mediaId: Long,
        size: Long,
        filename: String,
        mimeType: String,
    ) {
        if (size <= 0L) return
        val estimated = when {
            looksLikeMpegProgramStream(filename, mimeType) ->
                estimateMpegPsDurationMs(size)

            mimeType.contains("matroska", ignoreCase = true) ||
                    filename.endsWith(".mkv", ignoreCase = true) ->
                (size * 8_000L / TYPICAL_MKV_BITRATE_BPS).coerceAtMost(MAX_ESTIMATE_MS)

            mimeType.startsWith("video/") || filename.contains('.') ->
                (size * 8_000L / TYPICAL_PHONE_MP4_BITRATE_BPS).coerceAtMost(MAX_ESTIMATE_MS)

            else -> null
        }?.takeIf { it > 0L } ?: return
        saveDuration(mediaId, estimated)
        Log.i(TAG, "Estimated duration media=$mediaId → ${estimated}ms ($filename)")
    }

    private fun fetchDriveDurationMs(
        fileId: String,
        headers: Map<String, String>,
        filename: String,
        mimeType: String,
        sizeHint: Long,
    ): Long {
        if (fileId.isBlank()) return 0L
        val app = context.applicationContext as? GalleryApp ?: return 0L
        val url = "${GoogleDriveStorageProvider.DRIVE_FILES}/$fileId" +
                "?fields=size,videoMediaMetadata(durationMillis)"
        val request = Request.Builder().url(url).get().apply {
            headers.forEach { (k, v) -> header(k, v) }
        }.build()
        return try {
            app.httpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "Drive duration HTTP ${response.code} id=$fileId")
                    return 0L
                }
                val body = response.body?.string() ?: return 0L
                val parsed = gson.fromJson(body, DriveDurationDto::class.java)
                val raw = parsed?.videoMediaMetadata?.durationMillis?.takeIf { it > 0 } ?: 0L
                if (raw <= 0L) return 0L
                val size = parsed?.size?.toLongOrNull()?.takeIf { it > 0 } ?: sizeHint
                // Не режем через bestVideoDurationMs слишком жёстко: Drive phone-mp4
                // иногда даёт низкий bitrate в метаданных — всё равно показываем.
                plausibleVideoDurationMs(size, raw)
                    ?: raw.takeIf { it in 1L..MAX_ESTIMATE_MS }
                    ?: 0L
            }
        } catch (ex: Throwable) {
            Log.w(TAG, "Drive duration failed id=$fileId", ex)
            0L
        }
    }

    private suspend fun saveDuration(mediaId: Long, durationMs: Long) {
        val media = mediaDao.getById(mediaId) ?: return
        val video = media.video ?: return
        val next = bestVideoDurationMs(
            media.size,
            media.filename,
            media.mimeType,
            durationMs,
        )
        if (next <= 0L) return
        if (video.duration == next) return
        if (video.duration > 0L &&
            plausibleVideoDurationMs(media.size, video.duration) != null &&
            next > video.duration * 11L / 10L
        ) {
            return
        }
        mediaDao.upsert(media.copy(video = Video(duration = next)))
        cooldownUntil.remove(mediaId)
        Log.i(TAG, "Duration saved media=$mediaId → ${next}ms")
    }

    private suspend fun saveImageMeta(
        mediaId: Long,
        takenAt: Long?,
        width: Int,
        height: Int,
        orientation: Int,
    ) {
        val media = mediaDao.getById(mediaId) ?: return
        val img = media.image ?: return
        val nextTaken = positiveEpoch(media.takenAt) ?: positiveEpoch(takenAt)
        val nextW = img.width.takeIf { it > 0 } ?: width
        val nextH = img.height.takeIf { it > 0 } ?: height
        val nextOrient = if (img.orientation != 0) img.orientation else orientation
        val (sortDate, sortTime) = if (nextTaken != null) {
            mediaSortDateTime(nextTaken, media.modifiedAt, media.createdAt)
        } else {
            media.date to media.time
        }
        if (nextTaken == media.takenAt &&
            nextW == img.width &&
            nextH == img.height &&
            nextOrient == img.orientation
        ) {
            return
        }
        mediaDao.upsert(
            media.copy(
                takenAt = nextTaken,
                date = sortDate,
                time = sortTime,
                image = Image(width = nextW, height = nextH, orientation = nextOrient),
            )
        )
        cooldownUntil.remove(mediaId)
    }

    private fun supportsRange(url: String, headers: Map<String, String>): Boolean {
        val app = context.applicationContext as? GalleryApp ?: return false
        val request = Request.Builder()
            .url(url)
            .header("Range", "bytes=0-0")
            .head()
            .apply { headers.forEach { (k, v) -> header(k, v) } }
            .build()
        return try {
            app.httpClient.newCall(request).execute().use { response ->
                response.code == 206 ||
                        response.header("Accept-Ranges")
                            ?.contains("bytes", ignoreCase = true) == true ||
                        response.header("Content-Range") != null ||
                        // Drive alt=media часто 200 без Accept-Ranges, но Range всё же работает.
                        (response.isSuccessful && url.contains("googleapis.com"))
            }
        } catch (_: Throwable) {
            false
        }
    }

    private data class ProbeResult(val bitmap: Bitmap?, val durationMs: Long)

    private fun probe(
        url: String,
        headers: Map<String, String>,
        needThumb: Boolean,
        needDuration: Boolean,
    ): ProbeResult? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O_MR1) return null
        if (!needThumb && !needDuration) return null
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(url, HashMap(headers))
            val durationMs = if (needDuration) {
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.takeIf { it > 0L }
                    ?: 0L
            } else {
                0L
            }
            val bitmap = if (needThumb) {
                val width =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)
                        ?.toIntOrNull()
                val height =
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT)
                        ?.toIntOrNull()
                if (width != null && height != null && width > 0 && height > 0) {
                    val side = (context.resources.displayMetrics.widthPixels / 3).coerceAtLeast(128)
                    val scale = minOf(1f, side.toFloat() / minOf(width, height))
                    retriever.getScaledFrameAtTime(
                        0,
                        MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                        (width * scale).roundToInt().coerceAtLeast(1),
                        (height * scale).roundToInt().coerceAtLeast(1),
                    )
                } else {
                    null
                }
            } else {
                null
            }
            if (bitmap == null && durationMs <= 0L) null else ProbeResult(bitmap, durationMs)
        } catch (ex: Throwable) {
            Log.w(TAG, "probe failed", ex)
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private data class DriveDurationDto(
        val size: String? = null,
        val videoMediaMetadata: DriveDurationMeta? = null,
    )

    private data class DriveDurationMeta(
        @SerializedName("durationMillis") val durationMillis: Long? = null,
    )

    companion object {
        private const val TAG = "RemoteVideoThumb"
        private const val MAX_SIZE_BYTES = 2L * 1024 * 1024 * 1024
        private const val JOB_GAP_MS = 250L

        /** Короткий cooldown: иначе папка на сотни mp4 навсегда без цифр после одного fail. */
        private const val FAIL_COOLDOWN_MS = 2L * 60_000L
        private const val AUTH_COOLDOWN_MS = 60_000L
        private const val MAX_ESTIMATE_MS = 12L * 60 * 60 * 1000

        /** Типичный телефонный H.264/HEVC. */
        private const val TYPICAL_PHONE_MP4_BITRATE_BPS = 12_000_000L
        private const val TYPICAL_MKV_BITRATE_BPS = 8_000_000L
    }
}
