package com.asinosoft.photogallery.data

import android.content.Context
import android.text.format.Formatter
import coil3.SingletonImageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Категории временных файлов в `cacheDir` — как в CX: очистка по пунктам. */
enum class AppCacheKind {
    THUMBNAILS,
    IMAGE_CACHE,
    VLC_MEDIA,
    SHARE,
    OTHER,
}

data class AppCacheEntry(
    val kind: AppCacheKind,
    val bytes: Long,
) {
    fun formattedSize(context: Context): String =
        Formatter.formatShortFileSize(context, bytes.coerceAtLeast(0L))
}

object AppCacheCleanup {
    private val NAMED_DIRS = setOf(
        "thumbnails",
        "image_cache",
        "vlc_media",
        ShareCache.DIR_NAME,
    )

    suspend fun snapshot(context: Context): List<AppCacheEntry> = withContext(Dispatchers.IO) {
        val root = context.cacheDir
        listOf(
            AppCacheEntry(AppCacheKind.THUMBNAILS, dirSize(File(root, "thumbnails"))),
            AppCacheEntry(AppCacheKind.IMAGE_CACHE, dirSize(File(root, "image_cache"))),
            AppCacheEntry(AppCacheKind.VLC_MEDIA, dirSize(File(root, "vlc_media"))),
            AppCacheEntry(AppCacheKind.SHARE, dirSize(File(root, ShareCache.DIR_NAME))),
            AppCacheEntry(AppCacheKind.OTHER, otherSize(root)),
        )
    }

    suspend fun clear(context: Context, kind: AppCacheKind) = withContext(Dispatchers.IO) {
        val root = context.cacheDir
        when (kind) {
            AppCacheKind.THUMBNAILS -> deleteContents(File(root, "thumbnails"))
            AppCacheKind.IMAGE_CACHE -> {
                runCatching {
                    val loader = SingletonImageLoader.get(context)
                    loader.memoryCache?.clear()
                    loader.diskCache?.clear()
                }
                deleteContents(File(root, "image_cache"))
            }
            AppCacheKind.VLC_MEDIA -> deleteContents(File(root, "vlc_media"))
            AppCacheKind.SHARE -> deleteContents(File(root, ShareCache.DIR_NAME))
            AppCacheKind.OTHER -> deleteOther(root)
        }
    }

    suspend fun clearAll(context: Context) {
        for (kind in AppCacheKind.entries) {
            clear(context, kind)
        }
    }

    private fun otherSize(root: File): Long {
        if (!root.exists()) return 0L
        var total = 0L
        root.listFiles()?.forEach { child ->
            if (child.name in NAMED_DIRS) return@forEach
            total += if (child.isDirectory) dirSize(child) else child.length()
        }
        return total
    }

    private fun deleteOther(root: File) {
        if (!root.exists()) return
        root.listFiles()?.forEach { child ->
            if (child.name in NAMED_DIRS) return@forEach
            if (child.isDirectory) {
                deleteContents(child)
                child.delete()
            } else {
                child.delete()
            }
        }
    }

    private fun dirSize(dir: File): Long {
        if (!dir.exists()) return 0L
        var total = 0L
        dir.walkTopDown().forEach { file ->
            if (file.isFile) total += file.length()
        }
        return total
    }

    private fun deleteContents(dir: File) {
        if (!dir.exists()) return
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory) {
                deleteContents(child)
                child.delete()
            } else {
                child.delete()
            }
        }
    }
}
