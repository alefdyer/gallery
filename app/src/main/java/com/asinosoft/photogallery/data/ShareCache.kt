package com.asinosoft.photogallery.data

import android.content.Context
import android.util.Log
import java.io.File
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Кэш «Поделиться»: `cacheDir/share/{mediaId}/` и `share/transcoded/`.
 * После шаринга мессенджер ещё читает FileProvider URI — чистим с задержкой.
 * На старте / перед новой закачкой — LRU до [MAX_BYTES].
 */
object ShareCache {
    private const val TAG = "ShareCache"
    const val DIR_NAME = "share"
    const val MAX_BYTES = 400L * 1024 * 1024

    /** Дать Telegram/MAX скопировать файл. */
    private const val PURGE_AFTER_SHARE_MS = 3L * 60_000L
    private const val MAX_AGE_ON_START_MS = 24L * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val purgeGeneration = AtomicInteger(0)

    fun dir(context: Context): File =
        File(context.cacheDir, DIR_NAME).also { it.mkdirs() }

    /** При старте приложения: выкинуть старое и ужать до лимита. */
    fun trimOnStartup(context: Context) {
        scope.launch {
            deleteOlderThan(context, MAX_AGE_ON_START_MS)
            trim(context)
        }
    }

    /**
     * После chooser: через [PURGE_AFTER_SHARE_MS] снести весь share-кэш.
     * Новый share сдвигает таймер (generation).
     */
    fun schedulePurgeAfterShare(context: Context) {
        val appCtx = context.applicationContext
        val gen = purgeGeneration.incrementAndGet()
        scope.launch {
            delay(PURGE_AFTER_SHARE_MS)
            if (gen != purgeGeneration.get()) return@launch
            purgeAll(appCtx)
        }
    }

    /** Удалить самые старые файлы, пока сумма > [maxBytes]. */
    suspend fun trim(context: Context, maxBytes: Long = MAX_BYTES) = withContext(Dispatchers.IO) {
        val root = dir(context)
        if (!root.exists()) return@withContext
        deleteParts(root)
        deleteEmptyDirs(root)

        val files = allFiles(root).toMutableList()
        var total = files.sumOf { it.length() }
        if (total <= maxBytes) {
            Log.i(TAG, "trim ok total=$total max=$maxBytes")
            return@withContext
        }
        files.sortBy { it.lastModified() }
        for (file in files) {
            if (total <= maxBytes) break
            val size = file.length()
            if (file.delete()) {
                total -= size
                Log.i(TAG, "Evicted ${file.name} ($size), left=$total")
            }
        }
        deleteEmptyDirs(root)
    }

    private suspend fun purgeAll(context: Context) = withContext(Dispatchers.IO) {
        val root = dir(context)
        if (!root.exists()) return@withContext
        var removed = 0L
        allFiles(root).forEach { file ->
            val size = file.length()
            if (file.delete()) removed += size
        }
        deleteEmptyDirs(root)
        Log.i(TAG, "Purged after share, freed=$removed")
    }

    private suspend fun deleteOlderThan(context: Context, maxAgeMs: Long) =
        withContext(Dispatchers.IO) {
            val root = dir(context)
            if (!root.exists()) return@withContext
            val cutoff = System.currentTimeMillis() - maxAgeMs
            var removed = 0L
            allFiles(root).forEach { file ->
                if (file.lastModified() < cutoff) {
                    val size = file.length()
                    if (file.delete()) removed += size
                }
            }
            // Старые _h264.mp4 без v2 — мёртвый кэш после смены имени выхода.
            allFiles(root).forEach { file ->
                val n = file.name
                if (n.contains("_h264.mp4") && !n.contains("_h264_v2")) {
                    val size = file.length()
                    if (file.delete()) removed += size
                }
            }
            deleteEmptyDirs(root)
            if (removed > 0L) Log.i(TAG, "Deleted stale share cache, freed=$removed")
        }

    private fun allFiles(root: File): List<File> =
        root.walkTopDown().filter { it.isFile }.toList()

    private fun deleteParts(root: File) {
        allFiles(root).filter { it.name.endsWith(".part") }.forEach { part ->
            if (part.delete()) Log.i(TAG, "Removed part ${part.name}")
        }
    }

    private fun deleteEmptyDirs(root: File) {
        root.walkBottomUp()
            .filter { it.isDirectory && it != root }
            .forEach { dir ->
                val children = dir.list()
                if (children != null && children.isEmpty()) dir.delete()
            }
    }
}
