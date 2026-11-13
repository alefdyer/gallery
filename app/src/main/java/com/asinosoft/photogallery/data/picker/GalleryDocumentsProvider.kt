package com.asinosoft.photogallery.data.picker

import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.provider.DocumentsContract.Root
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.asinosoft.photogallery.BuildConfig
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.MediaDao
import com.asinosoft.photogallery.di.MediaDaoEntryPoint
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

/**
 * Корень в системном DocumentsUI («Найти файлы в других приложениях»).
 * Отдаёт локальные MediaStore-элементы из нашей БД.
 */
class GalleryDocumentsProvider : DocumentsProvider() {
    private lateinit var mediaDao: MediaDao

    override fun onCreate(): Boolean {
        val app = context?.applicationContext ?: return false
        mediaDao = EntryPointAccessors
            .fromApplication(app, MediaDaoEntryPoint::class.java)
            .mediaDao()
        return true
    }

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val cols = projection ?: DEFAULT_ROOT_PROJECTION
        return MatrixCursor(cols).apply {
            val row = newRow()
            row.add(Root.COLUMN_ROOT_ID, ROOT_ID)
            row.add(Root.COLUMN_DOCUMENT_ID, ROOT_ID)
            row.add(Root.COLUMN_TITLE, context!!.getString(R.string.app_name))
            row.add(Root.COLUMN_SUMMARY, context!!.getString(R.string.pick_media_summary))
            row.add(Root.COLUMN_ICON, R.mipmap.ic_launcher)
            row.add(Root.COLUMN_FLAGS, Root.FLAG_LOCAL_ONLY or Root.FLAG_SUPPORTS_RECENTS)
            row.add(Root.COLUMN_MIME_TYPES, "$MIME_IMAGE\n$MIME_VIDEO")
            row.add(Root.COLUMN_AVAILABLE_BYTES, null)
        }
    }

    override fun queryRecentDocuments(
        rootId: String,
        projection: Array<out String>?
    ): Cursor {
        if (rootId != ROOT_ID) throw FileNotFoundException("Unknown root $rootId")
        val items = runBlocking { mediaDao.getLocalPickable(RECENT_LIMIT) }
        return buildDocumentCursor(projection, items)
    }

    override fun queryDocument(
        documentId: String,
        projection: Array<out String>?
    ): Cursor {
        if (documentId == ROOT_ID) {
            return buildRootDocumentCursor(projection)
        }
        val media = mediaForDocumentId(documentId)
            ?: throw FileNotFoundException("No document $documentId")
        return buildDocumentCursor(projection, listOf(media))
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        if (parentDocumentId != ROOT_ID) {
            throw FileNotFoundException("Not a directory: $parentDocumentId")
        }
        val items = runBlocking { mediaDao.getLocalPickable(CHILD_LIMIT) }
        return buildDocumentCursor(projection, items)
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val media = mediaForDocumentId(documentId)
            ?: throw FileNotFoundException("No document $documentId")
        val uri = media.uri
            ?: throw FileNotFoundException("No uri for $documentId")
        return context!!.contentResolver.openFileDescriptor(uri, mode)
            ?: throw FileNotFoundException("Cannot open $uri")
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal?
    ): AssetFileDescriptor? {
        val media = mediaForDocumentId(documentId) ?: return null
        val uri = media.thumbnail ?: media.uri ?: return null
        return try {
            context!!.contentResolver.openTypedAssetFileDescriptor(uri, "image/*", null)
        } catch (_: Exception) {
            null
        }
    }

    override fun getDocumentType(documentId: String): String {
        if (documentId == ROOT_ID) return Document.MIME_TYPE_DIR
        return mediaForDocumentId(documentId)?.mimeType?.takeIf { it.isNotBlank() }
            ?: "application/octet-stream"
    }

    private fun mediaForDocumentId(documentId: String): Media? {
        val id = parseMediaId(documentId) ?: return null
        return runBlocking { mediaDao.getLocalPickableById(id) }
    }

    private fun buildRootDocumentCursor(projection: Array<out String>?): Cursor {
        val cols = projection ?: DEFAULT_DOCUMENT_PROJECTION
        return MatrixCursor(cols).apply {
            val row = newRow()
            includeRootDocument(row)
        }
    }

    private fun buildDocumentCursor(
        projection: Array<out String>?,
        items: List<Media>
    ): Cursor {
        val cols = projection ?: DEFAULT_DOCUMENT_PROJECTION
        return MatrixCursor(cols).apply {
            for (media in items) {
                includeMedia(newRow(), media)
            }
        }
    }

    private fun includeRootDocument(row: MatrixCursor.RowBuilder) {
        row.add(Document.COLUMN_DOCUMENT_ID, ROOT_ID)
        row.add(Document.COLUMN_DISPLAY_NAME, context!!.getString(R.string.app_name))
        row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR)
        row.add(Document.COLUMN_FLAGS, 0)
        row.add(Document.COLUMN_SIZE, null)
        row.add(Document.COLUMN_LAST_MODIFIED, null)
        row.add(Document.COLUMN_ICON, R.mipmap.ic_launcher)
    }

    private fun includeMedia(row: MatrixCursor.RowBuilder, media: Media) {
        val mime = media.mimeType.ifBlank {
            guessMime(media.filename) ?: "application/octet-stream"
        }
        var flags = Document.FLAG_SUPPORTS_THUMBNAIL
        if (mime.startsWith("image/") || mime.startsWith("video/")) {
            flags = flags or Document.FLAG_SUPPORTS_THUMBNAIL
        }
        row.add(Document.COLUMN_DOCUMENT_ID, documentIdFor(media.id))
        row.add(Document.COLUMN_DISPLAY_NAME, media.filename.substringAfterLast('/'))
        row.add(Document.COLUMN_MIME_TYPE, mime)
        row.add(Document.COLUMN_FLAGS, flags)
        row.add(Document.COLUMN_SIZE, media.size.takeIf { it >= 0 })
        row.add(
            Document.COLUMN_LAST_MODIFIED,
            media.modifiedAt ?: media.takenAt ?: media.createdAt
        )
        row.add(Document.COLUMN_ICON, 0)
    }

    companion object {
        val AUTHORITY: String = "${BuildConfig.APPLICATION_ID}.documents"
        const val ROOT_ID = "root"
        private const val MEDIA_PREFIX = "media:"
        private const val MIME_IMAGE = "image/*"
        private const val MIME_VIDEO = "video/*"
        private const val RECENT_LIMIT = 64
        private const val CHILD_LIMIT = 500

        private val DEFAULT_ROOT_PROJECTION = arrayOf(
            Root.COLUMN_ROOT_ID,
            Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_TITLE,
            Root.COLUMN_SUMMARY,
            Root.COLUMN_FLAGS,
            Root.COLUMN_ICON,
            Root.COLUMN_MIME_TYPES,
            Root.COLUMN_AVAILABLE_BYTES,
        )

        private val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_DISPLAY_NAME,
            Document.COLUMN_MIME_TYPE,
            Document.COLUMN_FLAGS,
            Document.COLUMN_SIZE,
            Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_ICON,
        )

        fun documentIdFor(mediaId: Long): String = "$MEDIA_PREFIX$mediaId"

        fun parseMediaId(documentId: String): Long? =
            documentId.removePrefix(MEDIA_PREFIX).toLongOrNull()
                .takeIf { documentId.startsWith(MEDIA_PREFIX) }

        fun documentUri(mediaId: Long) =
            DocumentsContract.buildDocumentUri(AUTHORITY, documentIdFor(mediaId))

        private fun guessMime(filename: String): String? {
            val ext = filename.substringAfterLast('.', "").lowercase()
            if (ext.isEmpty()) return null
            return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext)
        }
    }
}
