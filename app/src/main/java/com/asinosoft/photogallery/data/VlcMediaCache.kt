package com.asinosoft.photogallery.data

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Кэш скачанных remote-видео для LibVLC (только `cacheDir/vlc_media`).
 * Миниатюры и Coil сюда не входят.
 */
object VlcMediaCache {
    private const val TAG = "VlcMediaCache"
    private const val DIR_NAME = "vlc_media"

    /** Полный кэш скачанных .MPG и т.п. */
    const val MAX_BYTES = 300L * 1024 * 1024

    fun dir(context: Context): File =
        File(context.cacheDir, DIR_NAME).also { it.mkdirs() }

    fun fileFor(context: Context, mediaId: Long, filename: String): File {
        val safe = filename.ifBlank { "video.bin" }.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return File(dir(context), "${mediaId}_$safe")
    }

    /** Обновить mtime при cache hit — для LRU. */
    fun touch(file: File) {
        if (file.exists()) file.setLastModified(System.currentTimeMillis())
    }

    /**
     * Удаляет самые старые файлы, пока сумма > [MAX_BYTES].
     * [keep] не удаляется (только что скачанный / играющий).
     */
    fun trim(context: Context, keep: File? = null) {
        val root = dir(context)
        val files = root.listFiles()
            ?.filter { it.isFile }
            ?.toMutableList()
            ?: return

        // Битые недокачанные .part — сразу
        files.filter { it.name.endsWith(".part") && it != keep }.forEach { part ->
            if (part.delete()) Log.i(TAG, "Removed part ${part.name}")
        }

        val remaining = root.listFiles()?.filter { it.isFile }?.toMutableList() ?: return
        var total = remaining.sumOf { it.length() }
        if (total <= MAX_BYTES) return

        remaining.sortBy { it.lastModified() }
        for (file in remaining) {
            if (total <= MAX_BYTES) break
            if (keep != null && file.absolutePath == keep.absolutePath) continue
            val size = file.length()
            if (file.delete()) {
                total -= size
                Log.i(TAG, "Evicted ${file.name} ($size bytes), left=$total")
            }
        }
    }
}
