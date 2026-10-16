package com.asinosoft.gallery.data.backup

/**
 * Версия 1 резервной копии структуры галереи.
 * Медиа идентифицируются по path/name/storageType, не по локальным Room id.
 */
data class GallerySettingsBackup(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val categories: List<BackupCategory> = emptyList(),
    val categoryOrder: List<String> = emptyList(),
    val alwaysExpandedCategories: List<String> = emptyList(),
    val albums: List<BackupAlbum> = emptyList(),
    val favorites: List<BackupMediaRef> = emptyList(),
    val privatePaths: List<String> = emptyList(),
)

data class BackupCategory(
    val name: String,
)

data class BackupAlbum(
    val name: String,
    val category: String,
    val userCreated: Boolean = false,
    val media: List<BackupMediaRef> = emptyList(),
)

data class BackupMediaRef(
    val path: String,
    val name: String,
    val storageType: String = "LOCAL",
    val owner: String? = null,
    val size: Long? = null,
)

data class SettingsBackupResult(
    val albumsRestored: Int = 0,
    val mediaLinked: Int = 0,
    val favoritesRestored: Int = 0,
    val mediaNotFound: Int = 0,
    val privateManifestCount: Int = 0,
)
