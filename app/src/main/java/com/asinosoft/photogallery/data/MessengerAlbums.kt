package com.asinosoft.photogallery.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.asinosoft.photogallery.GalleryApp
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Альбомы из папок мессенджеров попадают в отдельную категорию.
 * Категория создаётся только при появлении первой такой папки; в базе хранится
 * системный ключ [AlbumCategory.MESSENGERS_NAME], а подпись берётся из строк локали.
 */
@Singleton
class MessengerAlbums @Inject constructor(
    private val albumDao: AlbumDao,
    @param:ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mutex = Mutex()

    fun isMessengerMedia(media: Media): Boolean = isMessenger(media.path, media.owner)

    suspend fun albumFor(name: String, messenger: Boolean): Album =
        albumDao.findAlbumByName(name)
            ?: albumDao.getOrCreateAlbum(name, if (messenger) categoryId() else AlbumCategory.OTHER.id)

    suspend fun migrateOnce() {
        migrateLegacyMessengersCategory()

        if (prefs.getBoolean(KEY_MIGRATED, false)) return

        val albumIds = albumDao.getAlbumMediaSources(AlbumCategory.OTHER.id)
            .filter { isMessenger(it.path, it.owner) }
            .map { it.albumId }
            .distinct()

        if (albumIds.isNotEmpty()) {
            val categoryId = categoryId()
            albumIds.forEach { albumId ->
                runCatching {
                    albumDao.upsert(albumDao.getAlbumById(albumId).copy(categoryId = categoryId))
                }.onFailure { Log.w(GalleryApp.TAG, "Can't move album $albumId to messengers", it) }
            }
        }

        prefs.edit { putBoolean(KEY_MIGRATED, true) }
    }

    /** Переводит старые локализованные имена («Мессенджеры» и т.п.) в системный ключ. */
    private suspend fun migrateLegacyMessengersCategory() = mutex.withLock {
        val savedId = prefs.getLong(KEY_CATEGORY_ID, 0L)
        if (savedId > 0) {
            albumDao.getCategoryById(savedId)?.let { migrateLegacyName(it) }
        }
        LEGACY_MESSENGERS_NAMES.forEach { name ->
            albumDao.findCategoryByName(name)?.let { migrateLegacyName(it) }
        }
    }

    private suspend fun categoryId(): Long = mutex.withLock {
        val savedId = prefs.getLong(KEY_CATEGORY_ID, 0L)
        if (savedId > 0) {
            val saved = albumDao.getCategoryById(savedId)
            if (null != saved) {
                migrateLegacyName(saved)
                return@withLock savedId
            }
        }

        val existing = albumDao.findCategoryByName(AlbumCategory.MESSENGERS_NAME)
            ?: LEGACY_MESSENGERS_NAMES.firstNotNullOfOrNull { albumDao.findCategoryByName(it) }

        val id = if (null != existing) {
            migrateLegacyName(existing)
            existing.id
        } else {
            albumDao.createCategory(AlbumCategory(name = AlbumCategory.MESSENGERS_NAME))
        }
        prefs.edit { putLong(KEY_CATEGORY_ID, id) }
        id
    }

    private suspend fun migrateLegacyName(category: AlbumCategory) {
        if (category.name != AlbumCategory.MESSENGERS_NAME && category.name in LEGACY_MESSENGERS_NAMES) {
            runCatching { albumDao.renameCategory(category.id, AlbumCategory.MESSENGERS_NAME) }
                .onFailure {
                    Log.w(GalleryApp.TAG, "Can't migrate messengers category ${category.id}", it)
                }
        }
    }

    private fun isMessenger(path: String, owner: String?): Boolean {
        val segments = path.split('/').filter { it.isNotEmpty() }
        if (segments.any { segment -> isMessengerSegment(segment) }) return true

        val folder = segments.lastOrNull() ?: return false
        return owner in MESSENGER_PACKAGES && GENERIC_FOLDERS.none { it.equals(folder, ignoreCase = true) }
    }

    private fun isMessengerSegment(segment: String): Boolean {
        val lower = segment.lowercase()
        return lower in MESSENGER_PACKAGES ||
            MESSENGER_MARKERS.any { lower.contains(it) } ||
            MESSENGER_FOLDERS.any { it.equals(segment, ignoreCase = true) }
    }

    private companion object {
        const val PREFS_NAME = "messengers"
        const val KEY_CATEGORY_ID = "category_id"
        const val KEY_MIGRATED = "migrated"

        /** Ранее имя категории писалось локализованной строкой — узнаём все наши переводы. */
        val LEGACY_MESSENGERS_NAMES = setOf(
            "Messengers",
            "Мессенджеры",
            "Messenger",
            "Messageries",
            "Mensajeros",
            "Mensageiros",
            "Komunikatory",
            "المراسلات",
            "मैसेजिंग ऐप",
            "メッセンジャー",
            "메신저",
            "即时通讯",
        )

        val MESSENGER_PACKAGES = setOf(
            "org.telegram.messenger",
            "org.telegram.messenger.web",
            "org.telegram.plus",
            "org.thunderdog.challegram",
            "com.whatsapp",
            "com.whatsapp.w4b",
            "com.viber.voip",
            "org.thoughtcrime.securesms",
            "com.vkontakte.android",
            "com.vk.im",
            "ru.oneme.app",
            "ru.ok.android",
            "com.facebook.orca",
            "com.tencent.mm",
            "jp.naver.line.android",
            "com.discord",
            "com.skype.raider",
            "com.imo.android.imoim",
            "ch.threema.app",
            "com.kakao.talk",
            "chat.tamtam",
            "im.vector.app",
            "com.icq.mobile.client",
            "com.wire",
        )

        val MESSENGER_MARKERS = listOf(
            "telegram",
            "whatsapp",
            "viber",
            "signal",
            "wechat",
            "weixin",
            "discord",
            "skype",
            "threema",
            "kakaotalk",
            "tamtam",
        )

        val MESSENGER_FOLDERS = setOf(
            "VK",
            "VKontakte",
            "Max",
            "Messenger",
            "LINE",
            "imo",
            "ICQ",
            "Element",
            "Wire",
        )

        val GENERIC_FOLDERS = setOf(
            "Camera",
            "DCIM",
            "Pictures",
            "Movies",
            "Download",
            "Downloads",
            "Screenshots",
            "Screen recordings",
            "ScreenRecorder",
        )
    }
}
