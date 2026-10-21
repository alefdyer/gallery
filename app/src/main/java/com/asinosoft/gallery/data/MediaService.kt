package com.asinosoft.gallery.data

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.edit
import com.asinosoft.gallery.GalleryApp
import com.asinosoft.gallery.data.storage.StorageProvider
import com.asinosoft.gallery.di.IntentHelper
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import kotlin.system.measureTimeMillis

class MediaService @Inject constructor(
    private val albumDao: AlbumDao,
    private val albumService: AlbumService,
    private val mediaDao: MediaDao,
    private val messengerAlbums: MessengerAlbums,
    private val privateAlbum: PrivateAlbum,
    private val trash: Trash,
    private val analytics: AppAnalytics,
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
        val albumName = media.path.split('/').lastOrNull { it.isNotEmpty() }
        albumName?.let {
            val album = messengerAlbums.albumFor(albumName, messengerAlbums.isMessengerMedia(media))
            addToAlbum(listOf(mediaId), album.id)
        }

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
            albums.forEach {albumId ->
                albumService.updateAlbumCover(albumId)
                albumDao.updateAlbumStats(albumId)
            }
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
        val items = media.mapNotNull { item -> item.uri?.let { it to item.mimeType } }
        if (items.isEmpty()) return

        val uris = items.map { it.first }
        val type = shareMimeType(items.map { it.second })
        val send =
            if (1 == uris.size) {
                Intent(Intent.ACTION_SEND).apply {
                    this.type = type
                    putExtra(Intent.EXTRA_STREAM, uris.first())
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData = ClipData.newUri(context.contentResolver, null, uris.first())
                }
            } else {
                Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                    this.type = type
                    putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    clipData =
                        ClipData.newUri(context.contentResolver, null, uris.first()).also { clip ->
                            uris.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                        }
                }
            }
        val chooser = Intent.createChooser(send, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
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
            albums.forEach {
                albumService.updateAlbumCover(it)
                albumDao.updateAlbumStats(it)
            }
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
        albumService.updateAlbumCover(albumId)
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
        albumService.updateAlbumCover(albumId)
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
        Log.i("MediaService", "update: ${provider.storage}")
        val updated = mutableSetOf<Long>()
        ThumbnailCache.startPreload(provider.getMediaCount() ?: 0)
        try {
            messengerAlbums.migrateOnce()
            provider.fetchAll().chunked(500).collect { fetched ->
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
                        val albumName = media.path.split('/').lastOrNull { it.isNotEmpty() }
                        albumName?.let {
                            val rawId = rawMediaIds[index]
                            val realId = if (rawId > 0) rawId else mediaDao.getMediaId(
                                media.storageId,
                                media.storageItemId
                            ) ?: -1L
                            if (realId > 0) {
                                val key = albumName to messengerAlbums.isMessengerMedia(media)
                                chunkAlbums.getOrPut(key) { mutableSetOf() } += realId
                            }
                        }
                    }

                    chunkAlbums.forEach { (key, ids) ->
                        val album = messengerAlbums.albumFor(key.first, key.second)
                        addToAlbum(ids, album.id)
                    }

                    updated += validMediaIds

                    val toPreload = toInsert.mapIndexedNotNull { index, media ->
                        val rawId = rawMediaIds[index]
                        val realId = if (rawId > 0) rawId else mediaDao.getMediaId(
                            media.storageId,
                            media.storageItemId
                        ) ?: -1L
                        if (realId > 0) ThumbnailCache.Request(
                            realId,
                            media.uri,
                            media.video != null
                        ) else null
                    }

                    if (toPreload.isNotEmpty()) {
                        ThumbnailCache.preloadBatch(context, toPreload)
                    }
                }
            }

            mediaDao.deleteAllExcept(provider.storage.id, updated)
            repairFolderAlbumsOnce(provider.storage.id)
            mergeDuplicateAlbumsOnce()
            albumService.deleteEmptyAlbums()

        } finally {
            ThumbnailCache.finishPreload()
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
            albumService.updateAlbumCover(target.id)
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

        val albums = HashMap<Pair<String, Boolean>, MutableSet<Long>>()
        mediaDao.getMediaWithoutAlbum(storageId).forEach { media ->
            val albumName = media.path.split('/').lastOrNull { it.isNotEmpty() } ?: return@forEach
            albums.getOrPut(albumName to messengerAlbums.isMessengerMedia(media)) { mutableSetOf() } += media.id
        }
        albums.forEach { (key, ids) ->
            addToAlbum(ids, messengerAlbums.albumFor(key.first, key.second).id)
        }

        prefs.edit { putBoolean(prefsKey, true) }
    }

    private companion object {
        const val LOCAL_STORAGE_ID = 1L
        const val PREFS_NAME = "media_service"
        const val KEY_ALBUMS_REPAIRED = "albums_repaired_"
        const val KEY_DUPLICATES_MERGED = "duplicates_merged"
    }
}
