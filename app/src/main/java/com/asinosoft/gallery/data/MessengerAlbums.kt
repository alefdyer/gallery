package com.asinosoft.gallery.data

import android.content.Context
import android.util.Log
import androidx.core.content.edit
import com.asinosoft.gallery.GalleryApp
import com.asinosoft.gallery.R
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Альбомы из папок мессенджеров попадают в отдельную категорию.
 * Категория создаётся только при появлении первой такой папки, её id хранится
 * в настройках, поэтому пользователь может её переименовать.
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
        if (messenger) {
            albumDao.findAlbumByName(name) ?: albumDao.getOrCreateAlbum(name, categoryId())
        } else {
            albumDao.getOrCreateAlbum(name, AlbumCategory.OTHER.id)
        }

    suspend fun migrateOnce() {
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

    private suspend fun categoryId(): Long = mutex.withLock {
        val savedId = prefs.getLong(KEY_CATEGORY_ID, 0L)
        if (savedId > 0 && null != albumDao.getCategoryById(savedId)) {
            return@withLock savedId
        }

        val name = context.getString(R.string.messengers)
        val id = albumDao.findCategoryByName(name)?.id
            ?: albumDao.createCategory(AlbumCategory(name = name))
        prefs.edit { putLong(KEY_CATEGORY_ID, id) }
        id
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
