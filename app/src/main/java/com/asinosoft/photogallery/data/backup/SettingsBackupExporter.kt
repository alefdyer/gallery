package com.asinosoft.photogallery.data.backup

import android.content.Context
import com.asinosoft.photogallery.data.AlbumDao
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.MediaDao
import com.asinosoft.photogallery.data.PrivateAlbum
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SettingsBackupExporter @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val albumDao: AlbumDao,
    private val mediaDao: MediaDao,
    private val privateAlbum: PrivateAlbum,
) {
    suspend fun export(
        onProgress: (current: Int, total: Int) -> Unit = { _, _ -> },
    ): GallerySettingsBackup = withContext(Dispatchers.IO) {
        val categories = albumDao.getAllCategories()
        val categoryById = categories.associateBy { it.id }
        val albums = albumDao.getAllAlbums()
        val prefs = context.getSharedPreferences(CATEGORY_PREFS, Context.MODE_PRIVATE)

        val orderIds = prefs.getString(KEY_ORDER, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            .orEmpty()
        val expandedIds = prefs.getString(KEY_EXPANDED, null)
            ?.split(',')
            ?.mapNotNull { it.toLongOrNull() }
            ?.toSet()
            .orEmpty()

        // альбомы + избранное + личные пути
        val total = albums.size + 2
        var current = 0
        onProgress(current, total)

        val backupAlbums = albums.map { album ->
            val categoryName = categoryById[album.categoryId]?.name ?: ":other"
            BackupAlbum(
                name = album.name,
                category = categoryName,
                userCreated = album.userCreated,
                media = albumDao.getAlbumMediaRefs(album.id).map { ref ->
                    BackupMediaRef(
                        path = ref.path,
                        name = ref.filename.substringAfterLast('/'),
                        storageType = ref.storageType.name,
                        owner = ref.owner,
                        size = ref.size.takeIf { it >= 0 }
                    )
                }
            ).also {
                current++
                onProgress(current, total)
            }
        }

        val favorites = mediaDao.getFavoritesList().map { it.toRef() }
        current++
        onProgress(current, total)

        val privatePaths = privateAlbum.listRelativePaths()
        current++
        onProgress(current, total)

        GallerySettingsBackup(
            categories = categories.map { BackupCategory(name = it.name) },
            categoryOrder = orderIds.mapNotNull { id -> categoryById[id]?.name },
            alwaysExpandedCategories = expandedIds.mapNotNull { id -> categoryById[id]?.name },
            albums = backupAlbums,
            favorites = favorites,
            privatePaths = privatePaths,
        )
    }

    private fun Media.toRef() = BackupMediaRef(
        path = path,
        name = filename.substringAfterLast('/'),
        storageType = storageType.name,
        owner = owner,
        size = size.takeIf { it >= 0 }
    )

    private companion object {
        const val CATEGORY_PREFS = "album_categories"
        const val KEY_ORDER = "order"
        const val KEY_EXPANDED = "expanded"
    }
}
