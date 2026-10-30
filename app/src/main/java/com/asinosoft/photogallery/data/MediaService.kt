package com.asinosoft.photogallery.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import coil3.SingletonImageLoader
import com.asinosoft.photogallery.GalleryApp
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.storage.StorageAuthProvider
import com.asinosoft.photogallery.data.storage.StorageDao
import com.asinosoft.photogallery.data.storage.StorageProvider
import com.asinosoft.photogallery.data.storage.StorageProviderRegistry
import com.asinosoft.photogallery.data.storage.StorageService
import com.asinosoft.photogallery.data.storage.StorageType
import com.asinosoft.photogallery.di.IntentHelper
import dagger.Lazy
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request

@Singleton
class MediaService @Inject constructor(
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
    private val storageDao: StorageDao,
    private val messengerAlbums: MessengerAlbums,
    private val storageAlbums: StorageAlbums,
    private val privateAlbum: PrivateAlbum,
    private val trash: Trash,
    private val analytics: AppAnalytics,
    private val remoteVideoThumbQueue: RemoteVideoThumbQueue,
    private val storageProviderRegistry: StorageProviderRegistry,
    private val storageService: Lazy<StorageService>,
    @param:ApplicationContext private val context: Context
) {
    private val intentHelper = IntentHelper
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun add(media: Media, syncFavorite: Boolean = false) {
        var mediaId = mediaDao.upsert(media)
        if (mediaId <= 0) {
            mediaId = mediaDao.getMediaId(media.storageId, media.storageItemId) ?: return
            if (syncFavorite) mediaDao.setFavorite(listOf(mediaId), media.favorite)
        }
        addToAlbum(
            listOf(mediaId),
            albumFor(folderAlbumName(media.path), media).id
        )

        CoroutineScope(Dispatchers.IO).launch {
            ThumbnailCache.preload(context, mediaId, media.uri, isVideo = media.video != null)
        }
    }

    /** На Android 11+ файлы попадают в системную корзину, откуда их можно восстановить в течение 30 дней */
    suspend fun delete(mediaIds: Collection<Long>, context: Context, callback: () -> Unit) {
        val uris = withContext(Dispatchers.IO) { mediaDao.getUris(mediaIds) }
        val onDeleted: suspend () -> Unit = {
            val albums = albumDao.getMediaAlbumIds(mediaIds)
            mediaDao.deleteAll(mediaIds)
            albums.forEach {
                albumDao.updateAlbumStats(it) }
            callback()
            if (mediaIds.isNotEmpty()) analytics.mediaDeleted(AppAnalytics.SOURCE_LIBRARY)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            intentHelper.trashRequest(uris, context, trashed = true) { granted ->
                if (granted) {
                    onDeleted()
                    trash.onTrashed(uris.size)
                }
            }
        } else {
            intentHelper.delete(uris, context, onDeleted)
        }
    }

    suspend fun edit(mediaId: Long, context: Context) {
        val uri = withContext(Dispatchers.IO) { mediaDao.getUri(mediaId) }
        val edit =
            Intent().apply {
                action = Intent.ACTION_EDIT
                data = uri
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        context.startActivity(edit)
    }

    suspend fun share(mediaIds: Collection<Long>, context: Context) {
        val media = withContext(Dispatchers.IO) { mediaDao.getByIds(mediaIds) }
        if (media.isEmpty()) return

        val needsDownload = media.any { !isLocalShareableUri(it.uri) }
        if (needsDownload) {
            MessageBus.emit(context.getString(R.string.share_preparing))
        }

        val items = media.mapNotNull { item ->
            runCatching { resolveShareContent(item) }
                .onFailure { Log.w(TAG, "Share resolve failed media=${item.id}", it) }
                .getOrNull()
        }
        if (items.isEmpty()) {
            MessageBus.emit(context.getString(R.string.share_failed))
            return
        }

        val uris = items.map { it.first }
        val type = shareMimeType(items.map { it.second })
        val send =
            if (1 == uris.size) {
                Intent(Intent.ACTION_SEND).apply {
                    this.type = type
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    // Явный MIME в ClipData — Telegram/MAX иначе иногда не читают URI.
                    clipData = ClipData(
                        "media",
                        arrayOf(type),
                        ClipData.Item(uris.first()),
                    )
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    this.type = type
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData(
                        "media",
                        arrayOf(type),
                        ClipData.Item(uris.first()),
                    ).also { clip ->
                        uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                    }
                }
            }
        startShareChooser(context, send, uris)
    }

    /**
     * Grant URI всем кандидатам chooser'а: WhatsApp часто прощает отсутствие,
     * Telegram/MAX — нет (особенно из Application context + NEW_TASK).
     */
    private fun startShareChooser(context: Context, send: Intent, uris: List<Uri>) {
        val grantFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
        val targets = context.packageManager.queryIntentActivities(
            send,
            PackageManager.MATCH_DEFAULT_ONLY,
        )
        for (uri in uris) {
            for (info in targets) {
                runCatching {
                    context.grantUriPermission(
                        info.activityInfo.packageName,
                        uri,
                        grantFlags,
                    )
                }
            }
        }
        val chooser = Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or grantFlags)
            clipData = send.clipData
        }
        // Chooser сам тоже должен читать URI.
        chooser.`package`?.let { pkg ->
            for (uri in uris) {
                runCatching { context.grantUriPermission(pkg, uri, grantFlags) }
            }
        }
        context.startActivity(chooser)
    }

    private fun shareMimeType(mimeTypes: List<String>): String {
        val types = mimeTypes.map { it.ifBlank { "*/*" } }.distinct()
        return when {
            types.size == 1 -> types.first()
            types.all { it.startsWith("image/") } -> "image/*"
            types.all { it.startsWith("video/") } -> "video/*"
            else -> "*/*"
        }
    }

    private fun mimeForShare(media: Media): String {
        val declared = media.mimeType.trim()
        if (declared.isNotEmpty() &&
            declared != "application/octet-stream" &&
            declared != "*/*"
        ) {
            return declared
        }
        val name = media.filename.lowercase()
        return when {
            name.endsWith(".mp4") || name.endsWith(".m4v") -> "video/mp4"
            name.endsWith(".mov") -> "video/quicktime"
            name.endsWith(".webm") -> "video/webm"
            name.endsWith(".mkv") -> "video/x-matroska"
            name.endsWith(".avi") -> "video/x-msvideo"
            name.endsWith(".mpg") || name.endsWith(".mpeg") -> "video/mpeg"
            name.endsWith(".3gp") -> "video/3gpp"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".gif") -> "image/gif"
            name.endsWith(".webp") -> "image/webp"
            media.video != null -> "video/*"
            media.image != null -> "image/*"
            else -> "*/*"
        }
    }

    private fun isLocalShareableUri(uri: Uri?): Boolean {
        val scheme = uri?.scheme?.lowercase() ?: return false
        return scheme == "content" || scheme == "file"
    }

    /** content/file URI или скачанный/сконвертированный файл + MIME. */
    private suspend fun resolveShareContent(media: Media): Pair<Uri, String>? {
        media.uri?.takeIf { isLocalShareableUri(it) }?.let { localUri ->
            // Локальный MediaStore — при HEVC тоже конвертим во временный файл.
            if (media.video == null) return localUri to mimeForShare(media)
            val copied =
                copyLocalToShareCache(media, localUri) ?: return localUri to mimeForShare(media)
            val converted = ShareVideoCompat.ensureMessengerCompatible(context, copied) {
                MessageBus.emit(context.getString(R.string.share_converting))
            } ?: run {
                error(context.getString(R.string.share_convert_failed))
            }
            return fileProviderUri(converted.first) to converted.second
        }

        val file = resolveShareFile(media) ?: return null
        if (media.video == null) {
            return fileProviderUri(file) to mimeForShare(media)
        }
        val converted = ShareVideoCompat.ensureMessengerCompatible(context, file) {
            MessageBus.emit(context.getString(R.string.share_converting))
        }
        if (converted == null) {
            error(context.getString(R.string.share_convert_failed))
        }
        val (ready, mime) = converted
        return fileProviderUri(ready) to mime
    }

    private suspend fun resolveShareFile(media: Media): File? {
        val cachedVlc = VlcMediaCache.fileFor(context, media.id, media.filename)
        // Уже скачан для LibVLC-плеера — берём его, даже если Drive size чуть врёт.
        if (cachedVlc.exists() &&
            cachedVlc.length() > 0L &&
            !looksLikeErrorPayload(cachedVlc)
        ) {
            return cachedVlc
        }

        if (media.storageType == StorageType.LOCAL ||
            media.storageId == EXTERNAL_STORAGE_ID
        ) {
            return null
        }

        return downloadRemoteForShare(media)
    }

    private suspend fun copyLocalToShareCache(media: Media, uri: Uri): File? =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir =
                    File(context.cacheDir, "$SHARE_CACHE_DIR/${media.id}").also { it.mkdirs() }
                val safe = media.filename.ifBlank { "media.bin" }
                    .replace(Regex("[^a-zA-Z0-9._-]"), "_")
                val out = File(dir, safe)
                if (isCompleteShareFile(out, media.size)) return@runCatching out
                context.contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(out).use { output -> input.copyTo(output) }
                } ?: return@runCatching null
                out.takeIf { it.exists() && it.length() > 0L }
            }.getOrNull()
        }

    private suspend fun downloadRemoteForShare(media: Media): File = withContext(Dispatchers.IO) {
        if (media.size > MAX_SHARE_DOWNLOAD_BYTES) {
            error(context.getString(R.string.share_too_large))
        }
        val service = storageService.get()
        checkNotNull(service.ensureFreshAuth(media.storageId)) {
            "Waiting for Google sign-in"
        }
        storageProviderRegistry.invalidate(media.storageId)
        val provider = storageProviderRegistry.getStorageProvider(media.storageId)
        val remoteUri = provider.getMediaUri(media)

        // Имя без префикса id — мессенджеры берут DISPLAY_NAME из FileProvider.
        val dir = File(context.cacheDir, "$SHARE_CACHE_DIR/${media.id}").also { it.mkdirs() }
        val safe = media.filename.ifBlank { "media.bin" }
            .replace(Regex("[^a-zA-Z0-9._-]"), "_")
        val out = File(dir, safe)
        if (isCompleteShareFile(out, media.size)) {
            return@withContext out
        }

        val tmp = File(dir, "$safe.part")
        tmp.delete()
        out.delete()
        val app = context.applicationContext as GalleryApp
        // Как у Exo: auth через network-interceptor (+ storage id), без ручного Bearer —
        // иначе на редиректе Drive/googleusercontent легко получить битое тело.
        val request = Request.Builder()
            .url(remoteUri.toString())
            .get()
            .header(StorageAuthProvider.HEADER_STORAGE_ID, media.storageId.toString())
            .build()
        Log.i(TAG, "Share download media=${media.id} → ${out.name}")
        app.httpClient.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("Download HTTP ${response.code}")
            }
            val body = response.body ?: error("Empty body")
            val contentLength = response.header("Content-Length")?.toLongOrNull()
                ?.takeIf { it > 0 }
            FileOutputStream(tmp).use { output ->
                body.byteStream().use { input -> input.copyTo(output) }
            }
            if (!tmp.exists() || tmp.length() <= 0L) {
                tmp.delete()
                error("Empty download")
            }
            // Content-Length — источник истины; media.size с Drive иногда врёт.
            if (contentLength != null && tmp.length() != contentLength) {
                Log.w(
                    TAG,
                    "Share size mismatch media=${media.id}: got ${tmp.length()} expected $contentLength"
                )
                tmp.delete()
                error(context.getString(R.string.share_failed))
            } else if (media.size > 0L &&
                contentLength == null &&
                tmp.length() != media.size
            ) {
                Log.w(
                    TAG,
                    "Share size≠Drive meta media=${media.id}: got ${tmp.length()} meta=${media.size}"
                )
            }
            if (looksLikeErrorPayload(tmp)) {
                tmp.delete()
                error(context.getString(R.string.share_failed))
            }
        }
        if (out.exists()) out.delete()
        if (!tmp.renameTo(out)) {
            tmp.copyTo(out, overwrite = true)
            tmp.delete()
        }
        if (looksLikeErrorPayload(out) || out.length() <= 0L) {
            out.delete()
            error(context.getString(R.string.share_failed))
        }
        out
    }

    private fun isCompleteShareFile(file: File, expectedSize: Long): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        if (looksLikeErrorPayload(file)) return false
        if (expectedSize > 0L && file.length() != expectedSize) return false
        return true
    }

    /** JSON/HTML вместо медиа (типичный сбой auth/редиректа Drive). */
    private fun looksLikeErrorPayload(file: File): Boolean {
        return runCatching {
            file.inputStream().use { input ->
                val buf = ByteArray(96)
                val n = input.read(buf)
                if (n <= 0) return true
                val head = buf.decodeToString(endIndex = n).trimStart()
                head.startsWith("{") ||
                        head.startsWith("<") ||
                        head.startsWith("error", ignoreCase = true)
            }
        }.getOrDefault(true)
    }

    private fun fileProviderUri(file: File): Uri =
        FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )

    suspend fun setFavorite(mediaIds: Collection<Long>, favorite: Boolean) {
        mediaDao.setFavorite(mediaIds, favorite)
        if (favorite && mediaIds.isNotEmpty()) analytics.favoriteAdded()
    }

    /** Для файлов из MediaStore отметку меняет система после подтверждения пользователя */
    suspend fun setFavoriteWithRequest(mediaIds: Collection<Long>, favorite: Boolean, callback: () -> Unit) {
        val (local, other) = mediaDao.getByIds(mediaIds).partition { it.isInMediaStore() }
        mediaDao.setFavorite(other.map { it.id }, favorite)
        if (local.isEmpty() || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            mediaDao.setFavorite(local.map { it.id }, favorite)
            if (favorite && mediaIds.isNotEmpty()) analytics.favoriteAdded()
            callback()
            return
        }
        val request = MediaStore.createFavoriteRequest(context.contentResolver, local.mapNotNull { it.uri }, favorite)
        intentHelper.launch(request.intentSender) { granted ->
            if (granted) {
                mediaDao.setFavorite(local.map { it.id }, favorite)
                if (favorite) analytics.favoriteAdded()
            }
            callback()
        }
    }

    /**
     * Файлы копируются в личный альбом, а оригиналы удаляются через системный запрос.
     * Если пользователь откажется, копии удаляются.
     */
    suspend fun moveToPrivate(mediaIds: Collection<Long>, context: Context, callback: () -> Unit) {
        val media = mediaDao.getByIds(mediaIds).filter { it.isInMediaStore() }
        if (media.isEmpty()) return

        val copies = mutableListOf<File>()
        try {
            media.forEach { copies += privateAlbum.copyIn(it) }
        } catch (ex: Throwable) {
            privateAlbum.discard(copies)
            throw ex
        }

        intentHelper.deleteRequest(media.mapNotNull { it.uri }, context) { granted ->
            if (!granted) {
                privateAlbum.discard(copies)
                return@deleteRequest
            }
            val ids = media.map { it.id }
            val albums = albumDao.getMediaAlbumIds(ids)
            mediaDao.deleteAll(ids)
            albums.forEach { albumDao.updateAlbumStats(it) }
            withContext(Dispatchers.IO) {
                ids.forEach { ThumbnailCache.getFile(context, it).delete() }
            }
            privateAlbum.onMovedIn(ids.size)
            callback()
        }
    }

    private fun Media.isInMediaStore() = LOCAL_STORAGE_ID == storageId && null != uri

    suspend fun addToAlbum(mediaIds: Collection<Long>, albumId: Long) {
        albumDao.addMediaToAlbum(mediaIds, albumId)
        albumDao.updateAlbumStats(albumId)
    }

    suspend fun addToNewAlbum(mediaIds: Collection<Long>, albumName: String, albumCategory: AlbumCategory) {
        val albumName = albumName.trim()
        require(albumName.isNotEmpty()) { "Album name must not be empty" }

        val albumId = albumDao.upsert(
            Album(name = albumName, categoryId = albumCategory.id, userCreated = true)
        )
        addToAlbum(mediaIds, albumId)
        analytics.albumCreated()
    }

    suspend fun addToNewAlbumInNewCategory(
        mediaIds: Collection<Long>,
        albumName: String,
        categoryName: String
    ) {
        val categoryName = categoryName.trim()
        require(categoryName.isNotEmpty()) { "Category name must not be empty" }

        val existing = albumDao.findCategoryByName(categoryName)
        val category = existing
            ?: AlbumCategory(
                id = albumDao.createCategory(AlbumCategory(name = categoryName)),
                name = categoryName
            )
        addToNewAlbum(mediaIds, albumName, category)
        if (existing == null) analytics.categoryCreated()
    }

    suspend fun removeFromAlbum(mediaIds: Collection<Long>, albumId: Long) {
        if (mediaIds.isEmpty()) {
            return
        }
        albumDao.removeMediaFromAlbum(mediaIds, albumId)
        albumDao.updateAlbumStats(albumId)
    }

    suspend fun updateAll(providers: Collection<StorageProvider>): Unit =
        withContext(Dispatchers.IO) {
            Log.d(GalleryApp.TAG, "rescan")
            measureTimeMillis {
                providers.forEach { update(it) }
            }.also {
                Log.i(GalleryApp.TAG, "DONE in $it ms")
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    suspend fun update(provider: StorageProvider) = withContext(Dispatchers.IO) {
        Log.i("MediaService", "update: ${provider.storage.type} id=${provider.storage.id}")
        val updated = mutableSetOf<Long>()
        ThumbnailCache.startPreload(provider.getMediaCount() ?: 0)
        try {
            messengerAlbums.migrateOnce()
            storageAlbums.migrateOnce()
            // Локально — крупные пачки; удалённые (WebDAV/облако) — по 10,
            // чтобы счётчики росли сразу после первых найденных файлов.
            val isLocal = provider.storage.type == StorageType.LOCAL
            val chunkSize = if (isLocal) LOCAL_FETCH_CHUNK else REMOTE_FETCH_CHUNK
            val isRemote = !isLocal
            if (isRemote) {
                storageAlbums.ensureCategory(provider.storage)
                SingletonImageLoader.get(context).memoryCache?.clear()
            }
            var remoteChunks = 0
            var fetchCompleted = false
            try {
            provider.fetchAll().chunked(chunkSize).collect { fetched ->
                val media = mediaDao.getMediaByStorageItemIds(
                    provider.storage.id,
                    fetched.map {
                        it.storageItemId
                    }
                )
                updated += media.map { it.id }

                if (provider.syncsFavorites) {
                    val fetchedFavorites = fetched.associate { it.storageItemId to it.favorite }
                    media.filter { fetchedFavorites[it.storageItemId] == !it.favorite }
                        .partition { it.favorite }
                        .let { (unfavorited, favorited) ->
                            mediaDao.setFavorite(unfavorited.map { it.id }, false)
                            mediaDao.setFavorite(favorited.map { it.id }, true)
                        }
                }

                val current = media.map { it.storageItemId }.toSet()
                val toInsert = fetched.filterNot { current.contains(it.storageItemId) }
                val existingByItemId = media.associateBy { it.storageItemId }
                val rootAlbum = context.getString(R.string.album_root)

                // Уже известные remote: обновить path/метаданные; альбом — только
                // если папка сменилась (полный remap на каждом refresh слишком дорог).
                if (isRemote) {
                    for (item in fetched) {
                        val existing = existingByItemId[item.storageItemId] ?: continue
                        val pathChanged = existing.path != item.path
                        val mergedDuration = bestVideoDurationMs(
                            item.size.takeIf { it > 0 } ?: existing.size,
                            item.filename,
                            item.mimeType,
                            existing.video?.duration ?: 0L,
                            item.video?.duration ?: 0L,
                        )
                        val mergedVideo = when {
                            item.video != null || existing.video != null ->
                                Video(duration = mergedDuration)

                            else -> null
                        }
                        val durationChanged =
                            (existing.video?.duration ?: 0L) != (mergedVideo?.duration ?: 0L)
                        if (pathChanged ||
                            durationChanged ||
                            existing.filename != item.filename ||
                            existing.size != item.size ||
                            existing.thumbnail != item.thumbnail
                        ) {
                            mediaDao.upsert(
                                item.copy(
                                    id = existing.id,
                                    favorite = existing.favorite,
                                    uri = item.uri ?: existing.uri,
                                    video = mergedVideo,
                                )
                            )
                        }
                        if (pathChanged) {
                            ensureRemoteFolderAlbum(
                                mediaId = existing.id,
                                albumName = folderAlbumName(item.path, rootAlbum),
                                storageId = provider.storage.id,
                            )
                        }
                    }
                }

                if (toInsert.isNotEmpty()) {
                    val rawMediaIds = mediaDao.upsertAll(toInsert)
                    val validMediaIds = rawMediaIds.mapIndexedNotNull { index, id ->
                        if (id > 0) id
                        else {
                            val item = toInsert[index]
                            mediaDao.getMediaId(item.storageId, item.storageItemId)
                        }
                    }

                    val chunkAlbums = HashMap<Pair<String, Boolean>, MutableSet<Long>>()
                    toInsert.forEachIndexed { index, media ->
                        val albumName = folderAlbumName(media.path, rootAlbum)
                        val rawId = rawMediaIds[index]
                        val realId = if (rawId > 0) rawId else mediaDao.getMediaId(
                            media.storageId,
                            media.storageItemId
                        ) ?: -1L
                        if (realId > 0) {
                            val key = albumName to
                                    if (isRemote) false else messengerAlbums.isMessengerMedia(
                                        media
                                    )
                            chunkAlbums.getOrPut(key) { mutableSetOf() } += realId
                        }
                    }

                    chunkAlbums.forEach { (key, ids) ->
                        val album = if (isRemote) {
                            storageAlbums.albumFor(key.first, provider.storage.id)
                        } else {
                            messengerAlbums.albumFor(key.first, key.second)
                        }
                        addToAlbum(ids, album.id)
                    }

                    updated += validMediaIds

                    // Миниатюры при скане — только для локальных content/file URI.
                    val toPreload = toInsert.mapIndexedNotNull { index, media ->
                        val uri = media.uri ?: return@mapIndexedNotNull null
                        val scheme = uri.scheme
                        if (scheme != "content" && scheme != "file") return@mapIndexedNotNull null
                        val rawId = rawMediaIds[index]
                        val realId = if (rawId > 0) rawId else mediaDao.getMediaId(
                            media.storageId,
                            media.storageItemId
                        ) ?: -1L
                        if (realId > 0) ThumbnailCache.Request(
                            realId,
                            uri,
                            media.video != null
                        ) else null
                    }

                    if (toPreload.isNotEmpty()) {
                        ThumbnailCache.preloadBatch(context, toPreload)
                    }
                }

                if (!isLocal) {
                    // Thumb из локального двойника — и для новых, и для уже известных
                    // (после «Обновить» toInsert пуст, иначе миниатюры так и не появятся).
                    val knownIds = media.associate { it.storageItemId to it.id }
                    val newItemIds = toInsert.map { it.storageItemId }.toSet()
                    for (item in fetched) {
                        val realId = knownIds[item.storageItemId]
                            ?: mediaDao.getMediaId(item.storageId, item.storageItemId)
                            ?: continue
                        if (realId <= 0) continue

                        val filled = fillRemoteThumbFromLocalTwin(realId, item)
                        if (!filled &&
                            item.storageItemId in newItemIds &&
                            item.thumbnail == null &&
                            item.video != null
                        ) {
                            remoteVideoThumbQueue.enqueue(
                                RemoteVideoThumbQueue.Job(
                                    mediaId = realId,
                                    storageId = item.storageId,
                                    storageItemId = item.storageItemId,
                                    size = item.size,
                                    uri = item.uri?.toString()
                                )
                            )
                        }
                    }

                    remoteChunks++
                    if (remoteChunks % REMOTE_CACHE_CLEAR_EVERY_CHUNKS == 0) {
                        SingletonImageLoader.get(context).memoryCache?.clear()
                    }
                }
            }
                fetchCompleted = true
            } catch (ex: Throwable) {
                Log.e("MediaService", "Fetch aborted: ${provider.storage}", ex)
                throw ex
            }

            // Не чистим базу, если обход оборвался ошибкой — иначе remote обнуляется.
            if (fetchCompleted) {
                mediaDao.deleteMissing(provider.storage.id, updated)
            }
            repairFolderAlbumsOnce(provider.storage.id)
            mergeDuplicateAlbumsOnce()
            albumDao.deleteEmptyAlbums()
            if (isRemote && fetchCompleted) {
                preloadAlbumCovers(provider)
            }

        } finally {
            ThumbnailCache.finishPreload()
        }
    }

    /**
     * Только обложки альбомов хранилища (coverId) — не все файлы, чтобы не жечь квоту API.
     */
    private suspend fun preloadAlbumCovers(provider: StorageProvider) {
        val category = albumDao.findCategoryByName(
            AlbumCategory.storageCategoryName(provider.storage.id)
        ) ?: return
        val albums = albumDao.getAllAlbums().filter { it.categoryId == category.id }
        val coverIds = albums.mapNotNull { it.coverId }.distinct()
        if (coverIds.isEmpty()) return

        val covers = mediaDao.getByIds(coverIds).associateBy { it.id }
        for (coverId in coverIds) {
            val file = ThumbnailCache.getFile(context, coverId)
            if (file.exists() && file.length() > 0) continue
            val media = covers[coverId] ?: continue
            val uri = media.thumbnail
                ?: runCatching { provider.getThumbnailUri(media) }.getOrNull()
                ?: continue
            val scheme = uri.scheme
            val isHttp = scheme == "http" || scheme == "https"
            // Полный remote-видео файл не качаем; native preview / локальный twin — ок.
            if (media.video != null && isHttp && media.thumbnail == null) continue
            ThumbnailCache.preload(
                context,
                coverId,
                uri,
                isVideo = media.video != null
            )
        }
    }

    /**
     * Раньше новые файлы искали альбом папки только в «Прочее», и если альбом
     * перенесли в другую категорию, в «Прочее» появлялся его дубль.
     */
    private suspend fun mergeDuplicateAlbumsOnce() {
        if (prefs.getBoolean(KEY_DUPLICATES_MERGED, false)) return

        albumDao.getDuplicateAlbums().groupBy { it.name }.values.forEach { albums ->
            val target = albums.first()
            albums.drop(1)
                .filter { AlbumCategory.OTHER.id == it.categoryId }
                .forEach { albumDao.mergeAlbums(it.id, target.id) }
        }

        prefs.edit { putBoolean(KEY_DUPLICATES_MERGED, true) }
    }

    /**
     * Сканирование, прерванное ошибкой, оставляло файлы в базе без альбомов,
     * а повторные сканирования такие файлы не трогают. Восстанавливаем один раз,
     * чтобы не возвращать в альбомы файлы, которые пользователь убрал сам.
     */
    private suspend fun repairFolderAlbumsOnce(storageId: Long) {
        val prefsKey = "$KEY_ALBUMS_REPAIRED$storageId"
        if (prefs.getBoolean(prefsKey, false)) return

        val rootAlbum = context.getString(R.string.album_root)
        val storageType = runCatching { storageDao.getStorageById(storageId).type }
            .getOrNull()
        val isRemote = storageType != null && storageType != StorageType.LOCAL
        val albums = HashMap<Pair<String, Boolean>, MutableSet<Long>>()
        mediaDao.getMediaWithoutAlbum(storageId).forEach { media ->
            val albumName = folderAlbumName(media.path, rootAlbum)
            val key = albumName to
                    if (isRemote) false else messengerAlbums.isMessengerMedia(media)
            albums.getOrPut(key) { mutableSetOf() } += media.id
        }
        albums.forEach { (key, ids) ->
            val (name, messenger) = key
            val album = if (isRemote) {
                storageAlbums.albumFor(name, storageId)
            } else {
                messengerAlbums.albumFor(name, messenger)
            }
            addToAlbum(ids, album.id)
        }

        prefs.edit { putBoolean(prefsKey, true) }
    }

    private suspend fun albumFor(name: String, media: Media): Album =
        if (media.storageType != StorageType.LOCAL) {
            storageAlbums.albumFor(name, media.storageId)
        } else {
            messengerAlbums.albumFor(name, messengerAlbums.isMessengerMedia(media))
        }

    /**
     * Файл в категории хранилища должен быть ровно в одном авто-альбоме папки.
     * Старые альбомы с Drive id вместо имени снимаются.
     */
    private suspend fun ensureRemoteFolderAlbum(
        mediaId: Long,
        albumName: String,
        storageId: Long,
    ) {
        val target = storageAlbums.albumFor(albumName, storageId)
        val linked = albumDao.getMediaAlbumIds(listOf(mediaId))
        for (albumId in linked) {
            if (albumId == target.id) continue
            val album = runCatching { albumDao.getAlbumById(albumId) }.getOrNull() ?: continue
            if (album.categoryId == target.categoryId && !album.userCreated) {
                albumDao.removeMediaFromAlbum(listOf(mediaId), albumId)
                albumDao.updateAlbumStats(albumId)
            }
        }
        if (target.id !in linked) {
            addToAlbum(listOf(mediaId), target.id)
        }
    }

    private fun folderAlbumName(
        path: String,
        rootAlbum: String = context.getString(R.string.album_root)
    ): String =
        albumNameFromPath(path, rootAlbum)

    /**
     * Копирует/генерирует thumb remote-файла из локального дубликата
     * (то же имя + size). Возвращает true, если файл thumb уже есть или удалось создать.
     */
    private suspend fun fillRemoteThumbFromLocalTwin(remoteId: Long, remote: Media): Boolean {
        val thumb = ThumbnailCache.getFile(context, remoteId)
        if (thumb.exists() && thumb.length() > 0) return true
        if (remote.size <= 0 || remote.filename.isBlank()) return false

        val local = mediaDao.findLocalByBasenameAndSize(remote.filename, remote.size)
            ?: return false
        if (ThumbnailCache.copyIfPresent(context, local.id, remoteId)) return true

        // Видеокадры при скане не декодируем — иначе refresh Drive на минуты (MediaCodec).
        if (local.video != null || remote.video != null) return false

        val uri = local.uri ?: return false
        val scheme = uri.scheme
        if (scheme != "content" && scheme != "file") return false
        ThumbnailCache.preload(context, remoteId, uri, isVideo = false)
        return thumb.exists() && thumb.length() > 0
    }

    private companion object {
        const val TAG = "MediaService"
        const val LOCAL_STORAGE_ID = 1L
        const val LOCAL_FETCH_CHUNK = 500
        const val REMOTE_FETCH_CHUNK = 50
        const val REMOTE_CACHE_CLEAR_EVERY_CHUNKS = 20
        const val PREFS_NAME = "media_service"
        const val KEY_ALBUMS_REPAIRED = "albums_repaired_"
        const val KEY_DUPLICATES_MERGED = "duplicates_merged"
        const val SHARE_CACHE_DIR = "share"
        const val MAX_SHARE_DOWNLOAD_BYTES = 2L * 1024 * 1024 * 1024
    }
}
