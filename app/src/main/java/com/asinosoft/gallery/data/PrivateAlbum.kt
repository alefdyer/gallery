package com.asinosoft.gallery.data

import android.content.ContentValues
import android.content.Context
import android.media.MediaMetadataRetriever
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.asinosoft.gallery.data.storage.StorageType
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Файлы личного альбома лежат во внутренней памяти приложения, куда у других
 * приложений нет доступа, и не попадают в MediaStore. Папка оригинала
 * сохраняется в структуре каталогов, чтобы вернуть файл туда же.
 */
@Singleton
class PrivateAlbum @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val root = File(context.filesDir, "private")
    private val version = MutableStateFlow(0)

    val media: Flow<List<Media>> = version
        .map { list() }
        .flowOn(Dispatchers.IO)

    private fun refresh() = version.update { it + 1 }

    private fun list(): List<Media> = root.walkTopDown()
        .filter { it.isFile }
        .map { it.toMedia() }
        .sortedWith(compareByDescending<Media> { it.date }.thenByDescending { it.time })
        .toList()

    /** Относительные пути файлов в личной папке (для манифеста бэкапа). */
    fun listRelativePaths(): List<String> =
        root.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(root).invariantSeparatorsPath }
            .sorted()
            .toList()

    suspend fun copyIn(media: Media): File = withContext(Dispatchers.IO) {
        val uri = requireNotNull(media.uri) { "Media ${media.id} has no uri" }
        val name = media.filename.substringAfterLast('/').ifEmpty { "${media.id}" }
        val dir = File(root, media.path.trim('/')).apply { mkdirs() }
        val target = uniqueFile(dir, name)
        try {
            val input = requireNotNull(context.contentResolver.openInputStream(uri)) { "Can't open $uri" }
            input.use { source -> target.outputStream().use { source.copyTo(it) } }
            target.setLastModified(
                LocalDateTime.of(media.date, media.time).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            )
        } catch (ex: Throwable) {
            target.delete()
            throw ex
        }
        target
    }

    private val _movedIn = MutableSharedFlow<Int>(extraBufferCapacity = 1)

    /** Количество файлов, только что перенесённых в личную папку */
    val movedIn: SharedFlow<Int> = _movedIn

    fun onMovedIn(count: Int) {
        refresh()
        _movedIn.tryEmit(count)
    }

    suspend fun discard(files: Collection<File>) = withContext(Dispatchers.IO) {
        files.forEach { it.delete() }
    }

    suspend fun restore(items: Collection<Media>) = withContext(Dispatchers.IO) {
        val errors = items.mapNotNull { media ->
            runCatching {
                val file = File(media.filename)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    restoreToMediaStore(file, media)
                } else {
                    restoreToFile(file, media)
                }
                file.delete()
            }.exceptionOrNull()
        }
        refresh()
        errors.firstOrNull()?.let { throw it }
    }

    suspend fun delete(items: Collection<Media>) = withContext(Dispatchers.IO) {
        items.forEach { File(it.filename).delete() }
        refresh()
    }

    private fun restoreToMediaStore(file: File, media: Media) {
        val video = null != media.video
        val collection = if (video) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val fallbackPath = (if (video) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_PICTURES) + "/"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, file.name)
            put(MediaStore.MediaColumns.MIME_TYPE, media.mimeType)
            put(MediaStore.MediaColumns.DATE_TAKEN, file.lastModified())
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = listOf(media.path, fallbackPath).firstNotNullOfOrNull { path ->
            values.put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            runCatching { resolver.insert(collection, values) }.getOrNull()
        } ?: error("Can't restore ${file.name}")

        try {
            val output = requireNotNull(resolver.openOutputStream(uri)) { "Can't write $uri" }
            output.use { target -> file.inputStream().use { it.copyTo(target) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (ex: Throwable) {
            resolver.delete(uri, null, null)
            throw ex
        }
    }

    @Suppress("DEPRECATION")
    private fun restoreToFile(file: File, media: Media) {
        val dir = File(Environment.getExternalStorageDirectory(), media.path.trim('/')).apply { mkdirs() }
        val target = uniqueFile(dir, file.name)
        file.copyTo(target)
        target.setLastModified(file.lastModified())
        MediaScannerConnection.scanFile(context, arrayOf(target.absolutePath), null, null)
    }

    private fun File.toMedia(): Media {
        val mimeType = MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            ?: "application/octet-stream"
        val video = mimeType.startsWith("video/")
        val datetime = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastModified()), ZoneId.systemDefault())
        val relativePath = parentFile?.relativeTo(root)?.invariantSeparatorsPath.orEmpty()
        return Media(
            id = -(absolutePath.hashCode().toLong() and 0xffffffffL) - 1,
            uri = Uri.fromFile(this),
            date = datetime.toLocalDate(),
            time = datetime.toLocalTime(),
            path = if (relativePath.isEmpty()) "" else "$relativePath/",
            size = length(),
            filename = absolutePath,
            mimeType = mimeType,
            storageId = EXTERNAL_STORAGE_ID,
            storageType = StorageType.LOCAL,
            storageItemId = absolutePath,
            image = if (video) null else Image(),
            video = if (video) Video(videoDuration()) else null
        )
    }

    private fun File.videoDuration(): Long {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(absolutePath)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            retriever.release()
        }
    }

    private fun uniqueFile(dir: File, name: String): File {
        var file = File(dir, name)
        var index = 1
        while (file.exists()) {
            file = File(dir, "${name.substringBeforeLast('.')} ($index)" +
                name.substringAfterLast('.', "").let { if (it.isEmpty()) "" else ".$it" })
            index++
        }
        return file
    }
}
