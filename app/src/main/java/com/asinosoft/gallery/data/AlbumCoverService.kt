package com.asinosoft.gallery.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlin.math.max
import androidx.core.graphics.withClip
import androidx.core.graphics.createBitmap

private const val COVER_DIR = "album-covers"
private const val COVER_SIZE = 600
private const val COVER_GAP = 4
private const val JPEG_QUALITY = 90

class AlbumCoverService @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    fun getAlbumCover(albumId: Long): File? =
        findCovers(albumId).maxByOrNull { it.lastModified() }

    fun deleteAlbumCover(albumId: Long) {
        findCovers(albumId).forEach { it.delete() }
        signatureFile(albumId).delete()
    }

    /** Удаляет предыдущие коллажи этого альбома, оставляя только что собранный. */
    fun deleteOtherCovers(albumId: Long, keep: File) {
        findCovers(albumId).filter { it.name != keep.name }.forEach { it.delete() }
        signatureFile(albumId).delete()
    }

    /**
     * Собирает обложку альбома и сохраняет её в приватный каталог приложения.
     * Четыре последних фото — сетка 2×2, или три-два-одно последних фото.
     */
    suspend fun updateAlbumCover(albumId: Long, sources: List<Media>): File? = withContext(Dispatchers.IO) {
        if (sources.isEmpty()) {
            deleteAlbumCover(albumId)
            return@withContext null
        }

        // Имя файла включает id снимков: путь в базе меняется, и Coil не держит старый кадр.
        val signature = sources.joinToString("-") { it.id.toString() }
        val file = coverFile(albumId, signature)
        if (isCurrent(file)) return@withContext file

        coverLock(albumId).withLock {
            if (isCurrent(file)) return@withContext file
            coverSemaphore.withPermit {
                renderCover(sources, file)
            }
        }
    }

    private suspend fun renderCover(
        sources: List<Media>,
        file: File,
    ): File? {
        val bitmaps = ArrayList<Bitmap>(sources.size)
        try {
            for (media in sources) {
                bitmaps += decodeThumbnail(media) ?: return null
            }
            val collage = compose(bitmaps)
            try {
                writeJpeg(file, collage)
            } finally {
                collage.recycle()
            }
        } finally {
            bitmaps.forEach { it.recycle() }
        }
        return file
    }

    private suspend fun decodeThumbnail(media: Media): Bitmap? {
        val cached = ThumbnailCache.getFile(context, media.id)
        if (!cached.exists() || cached.length() == 0L) {
            ThumbnailCache.preload(
                context,
                media.id,
                media.uri,
                isVideo = null != media.video,
            )
        }
        val file = ThumbnailCache.getFile(context, media.id)
        if (!file.exists() || file.length() == 0L) return null
        return BitmapFactory.decodeFile(file.absolutePath)
    }

    private fun compose(bitmaps: List<Bitmap>): Bitmap {
        val out = createBitmap(COVER_SIZE, COVER_SIZE)
        val canvas = Canvas(out)
        canvas.drawColor(Color.BLACK)
        val cell = (COVER_SIZE - COVER_GAP) / 2
        when (bitmaps.size) {
            1 -> drawCrop(canvas, bitmaps[0], Rect(0, 0, COVER_SIZE, COVER_SIZE))
            2 -> {
                drawCrop(canvas, bitmaps[0], Rect(0, 0, cell, COVER_SIZE))
                drawCrop(
                    canvas,
                    bitmaps[1],
                    Rect(cell + COVER_GAP, 0, COVER_SIZE, COVER_SIZE),
                )
            }
            else -> {
                val cells = listOf(
                    Rect(0, 0, cell, cell),
                    Rect(cell + COVER_GAP, 0, COVER_SIZE, cell),
                    Rect(0, cell + COVER_GAP, cell, COVER_SIZE),
                    Rect(cell + COVER_GAP, cell + COVER_GAP, COVER_SIZE, COVER_SIZE),
                )
                bitmaps.take(4).forEachIndexed { index, bitmap ->
                    drawCrop(canvas, bitmap, cells[index])
                }
            }
        }
        return out
    }

    private fun drawCrop(canvas: Canvas, src: Bitmap, dest: Rect) {
        if (src.width <= 0 || src.height <= 0) return
        val scale = max(
            dest.width().toFloat() / src.width,
            dest.height().toFloat() / src.height,
        )
        val drawnWidth = src.width * scale
        val drawnHeight = src.height * scale
        val left = dest.left + (dest.width() - drawnWidth) / 2f
        val top = dest.top + (dest.height() - drawnHeight) / 2f
        canvas.withClip(dest) {
            canvas.drawBitmap(
                src,
                null,
                RectF(left, top, left + drawnWidth, top + drawnHeight),
                Paint(Paint.FILTER_BITMAP_FLAG),
            )
        }
    }

    private fun writeJpeg(file: File, bitmap: Bitmap) {
        val tmp = File(coverDir(), "${file.name}.tmp")
        try {
            FileOutputStream(tmp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
            }
            if (file.exists() && !file.delete()) {
                error("Can't replace ${file.name}")
            }
            if (!tmp.renameTo(file)) {
                tmp.copyTo(file, overwrite = true)
            }
        } finally {
            if (tmp.exists()) tmp.delete()
        }
    }

    private fun isCurrent(file: File): Boolean = file.exists() && file.length() > 0L

    private fun coverDir(): File = File(context.filesDir, COVER_DIR).apply { mkdirs() }

    private fun coverFile(albumId: Long, signature: String): File =
        File(coverDir(), "$albumId-$signature.jpg")

    private fun signatureFile(albumId: Long): File = File(coverDir(), "$albumId.sig")

    private fun findCovers(albumId: Long): List<File> {
        val prefix = "$albumId-"
        val legacy = "$albumId.jpg"
        return coverDir().listFiles()?.filter { file ->
            file.isFile && file.extension == "jpg" &&
                (file.name == legacy || file.name.startsWith(prefix))
        }.orEmpty()
    }

    private fun coverLock(albumId: Long): Mutex =
        coverLocks.computeIfAbsent(albumId) { Mutex() }

    private companion object {
        val coverSemaphore = Semaphore(2)
        val coverLocks = ConcurrentHashMap<Long, Mutex>()
    }
}
