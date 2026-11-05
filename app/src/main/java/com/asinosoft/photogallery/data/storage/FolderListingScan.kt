package com.asinosoft.photogallery.data.storage

import com.asinosoft.photogallery.data.mediaParentPath
import com.thegrizzlylabs.sardineandroid.DavResource
import java.security.MessageDigest

data class FolderListingResult(
    val unchangedPaths: List<String>,
    val folders: List<FolderSnapshot>,
    val skippedByTime: Int,
)

/** Полный обход без sync-collection: папки с тем же списком не пишем в альбомы. */
interface FolderListingScan {
    fun setKnownFolders(folders: List<FolderSnapshot>)

    /**
     * true — сверять только дату и ETag папки.
     * false — всегда запрашивать список файлов.
     */
    fun setFastStampCheck(enabled: Boolean) {}

    fun takeFolderListing(): FolderListingResult?
}

class FolderScanState {
    private var known: Map<String, FolderSnapshot> = emptyMap()
    private val unchanged = ArrayList<String>()
    private val folders = LinkedHashMap<String, FolderSnapshot>()
    private var skippedByTime = 0
    private var active = false

    fun begin(saved: List<FolderSnapshot>) {
        known = saved.associateBy { it.path }
        unchanged.clear()
        folders.clear()
        skippedByTime = 0
        active = false
    }

    fun savedStamp(folderKey: String): String? =
        known[folderKey]?.dirStamp?.takeIf { it.isNotBlank() }

    fun savedChildUrls(folderKey: String): List<String> =
        known[folderKey]?.childUrls.orEmpty().split('\n').filter { it.isNotBlank() }

    /** Ответа на дату не было — оставляем прошлый снимок, файлы не удаляем. */
    fun keepSaved(folderKey: String): Boolean {
        val saved = known[folderKey] ?: return false
        active = true
        unchanged += folderKey
        folders[folderKey] = saved
        return true
    }

    /** Время и ETag папки те же — полный список файлов не нужен. */
    fun skipIfSameStamp(folderKey: String, dirStamp: String): Boolean {
        if (dirStamp.isBlank()) return false
        val saved = known[folderKey] ?: return false
        if (saved.dirStamp != dirStamp) return false
        active = true
        skippedByTime++
        unchanged += folderKey
        folders[folderKey] = saved
        return true
    }

    /** true — состав папки тот же, файлы из неё заново не отдаём. */
    fun onFolder(
        folderKey: String,
        parts: List<String>,
        dirStamp: String = "",
        childUrls: List<String> = emptyList(),
    ): Boolean {
        active = true
        val fingerprint = fingerprintOf(parts)
        folders[folderKey] = FolderSnapshot(
            path = folderKey,
            fingerprint = fingerprint,
            dirStamp = dirStamp,
            childUrls = childUrls.joinToString("\n"),
        )
        val same = known[folderKey]?.fingerprint == fingerprint
        if (same) unchanged += folderKey
        return same
    }

    fun take(): FolderListingResult? {
        if (!active) return null
        val result = FolderListingResult(unchanged.toList(), folders.values.toList(), skippedByTime)
        begin(emptyList())
        return result
    }

    companion object {
        fun fingerprintOf(parts: List<String>): String {
            val digest = MessageDigest.getInstance("SHA-256")
            for (part in parts.sorted()) {
                digest.update(part.toByteArray(Charsets.UTF_8))
                digest.update(0)
            }
            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        fun davPart(item: DavResource): String = buildString {
            append(if (item.isDirectory) 'd' else 'f')
            append('\t')
            append(item.name ?: "")
            append('\t')
            append(item.contentLength ?: -1L)
            append('\t')
            append(item.modified?.time ?: 0L)
            append('\t')
            append(item.etag ?: "")
        }

        /** ETag и дата самой папки. Пусто — сервер их не отдал, список нужен целиком. */
        fun dirStamp(item: DavResource?): String {
            if (item == null) return ""
            val etag = item.etag?.trim().orEmpty()
            val modified = item.modified?.time ?: 0L
            if (etag.isEmpty() && modified <= 0L) return ""
            return "$etag|$modified"
        }

        /**
         * Тот же формат, что [com.asinosoft.photogallery.data.Media.path]:
         * у вложенной папки на конце «/», корень — пустая строка.
         */
        fun folderKey(folderUrlPath: String?, rootPath: String): String {
            val folder = folderUrlPath?.trimEnd('/').orEmpty()
            if (folder.isEmpty()) return ""
            return mediaParentPath("$folder/.", rootPath)
        }
    }
}
