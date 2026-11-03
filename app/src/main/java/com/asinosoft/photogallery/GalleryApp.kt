package com.asinosoft.photogallery

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.svg.SvgDecoder
import coil3.video.VideoFrameDecoder
import com.asinosoft.photogallery.data.ShareCache
import com.asinosoft.photogallery.data.people.PeopleAlbums
import com.asinosoft.photogallery.data.people.PeopleIndexQueue
import android.util.Log
import androidx.core.net.toUri
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.data.storage.StorageAuthProvider
import com.asinosoft.photogallery.data.storage.StorageCredentialStore
import com.asinosoft.photogallery.data.storage.StorageDao
import com.asinosoft.photogallery.data.storage.StorageService
import com.asinosoft.photogallery.data.storage.StorageType
import com.yandex.mobile.ads.common.YandexAds
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@HiltAndroidApp
class GalleryApp : Application() {
    @Inject lateinit var storageAuthProvider: StorageAuthProvider
    @Inject
    lateinit var storageDao: StorageDao
    @Inject
    lateinit var storageService: StorageService

    @Inject
    lateinit var peopleIndexQueue: PeopleIndexQueue

    @Inject
    lateinit var peopleAlbums: PeopleAlbums

    @Inject
    lateinit var storageCredentials: StorageCredentialStore

    lateinit var httpClient: OkHttpClient
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val TAG = "gallery.app"
        private const val KEY_LOCAL_ALBUMS_REBUILT = "local_albums_rebuilt_v1"
        private const val KEY_STORAGES_RESTORED = "storages_restored_from_wipe_v1"
    }

    override fun onCreate() {
        super.onCreate()

        YandexAds.initialize(this) { }

        // Старый share-кэш (копии/h264) иначе копится на гигабайты.
        ShareCache.trimOnStartup(this)

        appScope.launch {
            runCatching { storageDao.migratePlaintextPasswords() }
            runCatching { restoreOrphanStoragesIfNeeded() }
            runCatching { storageService.backfillMissingGoogleDriveEmails() }
            runCatching { storageService.backfillMissingDropboxEmails() }
            runCatching { peopleAlbums.ensureCategory() }
            // После аварийного wipe БД альбомы/мессенджеры могли остаться без связей —
            // один раз пересканируем локальное хранилище.
            val mediaPrefs = getSharedPreferences("media_service", Context.MODE_PRIVATE)
            if (!mediaPrefs.getBoolean(KEY_LOCAL_ALBUMS_REBUILT, false)) {
                val ok = runCatching {
                    val locals = storageDao.getStorages().first()
                        .filter { it.type == StorageType.LOCAL }
                    android.util.Log.i(TAG, "rebuild local albums: ${locals.size} storage(s)")
                    locals.forEach { storageService.enqueueFetch(it) }
                }.onFailure {
                    android.util.Log.w(TAG, "rebuild local albums failed", it)
                }.isSuccess
                if (ok) mediaPrefs.edit { putBoolean(KEY_LOCAL_ALBUMS_REBUILT, true) }
            }
            peopleIndexQueue.startBackfill()
        }

        httpClient =
            OkHttpClient
                .Builder()
                // Network: Drive alt=media часто редиректит на googleusercontent —
                // application-interceptor не увидит второй hop, Bearer пропадёт.
                .addNetworkInterceptor { chain ->
                    chain.proceed(storageAuthProvider.authorize(chain.request()))
                }.build()

        SingletonImageLoader.setSafe {
            ImageLoader
                .Builder(baseContext)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .memoryCache(
                    MemoryCache
                        .Builder()
                        // Меньше запас под Coil: при WebDAV-видео ExoPlayer тоже ест кучу.
                        .maxSizePercent(this, 0.08)
                        .build()
                )
                .diskCachePolicy(CachePolicy.ENABLED)
                .diskCache {
                    DiskCache.Builder()
                        .directory(cacheDir.resolve("image_cache"))
                        .maxSizeBytes(500L * 1024 * 1024)
                        .build()
                }
                .components {
                    add(GifDecoder.Factory())
                    add(SvgDecoder.Factory())
                    add(VideoFrameDecoder.Factory())
                    add(OkHttpNetworkFetcherFactory({ httpClient }))
                }.build()
        }
    }

    /**
     * После wipe БД токены остались в EncryptedSharedPreferences (pwd_4..),
     * а строки storage пропали. Восстанавливаем метаданные из аварийного дампа
     * в том же порядке id, чтобы hydrate подхватил пароли.
     */
    private suspend fun restoreOrphanStoragesIfNeeded() {
        val prefs = getSharedPreferences("media_service", Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_STORAGES_RESTORED, false)) return

        val orphanIds = listOf(4L, 5L, 6L, 9L, 10L).filter {
            storageCredentials.get(it)?.isNotEmpty() == true
        }
        if (orphanIds.isEmpty()) {
            prefs.edit { putBoolean(KEY_STORAGES_RESTORED, true) }
            return
        }
        Log.i(TAG, "restoring orphan storages for ids=$orphanIds")

        // Порядок как в leaf page дампа: Dropbox → Drive → Nextcloud → WebDAV → Drive
        val candidates = listOf(
            Storage(
                id = 4,
                type = StorageType.DROPBOX,
                url = "https://api.dropboxapi.com/2/".toUri(),
                login = "asinosoft@gmail.com",
            ),
            Storage(
                id = 5,
                type = StorageType.GOOGLE_DRIVE,
                url = "https://www.googleapis.com/".toUri(),
                login = "asinosoft@gmail.com",
            ),
            Storage(
                id = 6,
                type = StorageType.NEXTCLOUD,
                url = "https://use20.thegood.cloud".toUri(),
                login = "as010201@gmail.com",
            ),
            Storage(
                id = 9,
                type = StorageType.WEBDAV,
                url = "https://cloud25.netcraze.club/webdav/".toUri(),
                login = "admin",
            ),
            Storage(
                id = 10,
                type = StorageType.GOOGLE_DRIVE,
                url = "https://www.googleapis.com/".toUri(),
                login = "as010201@gmail.com",
            ),
        )

        val existing = storageDao.getStorages().first().map { it.id }.toSet()
        for (storage in candidates) {
            if (storage.id !in orphanIds) continue
            if (storage.id in existing) continue
            storageDao.upsert(storage)
            Log.i(TAG, "restored storage id=${storage.id} type=${storage.type}")
            storageService.enqueueFetch(storage)
        }
        prefs.edit { putBoolean(KEY_STORAGES_RESTORED, true) }
    }
}
