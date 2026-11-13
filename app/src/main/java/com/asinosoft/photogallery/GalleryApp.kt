package com.asinosoft.photogallery

import android.app.Application
import android.util.Log
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
import com.asinosoft.photogallery.data.VlcNative
import com.asinosoft.photogallery.data.storage.GalleryStorageTag
import com.asinosoft.photogallery.data.storage.StorageAuthProvider
import com.asinosoft.photogallery.data.storage.StorageDao
import com.asinosoft.photogallery.data.storage.StorageService
import com.yandex.mobile.ads.common.YandexAds
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

@HiltAndroidApp
class GalleryApp : Application() {
    @Inject lateinit var storageAuthProvider: StorageAuthProvider
    @Inject
    lateinit var storageDao: StorageDao
    @Inject
    lateinit var storageService: StorageService

    lateinit var httpClient: OkHttpClient
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    companion object {
        const val TAG = "gallery.app"
    }

    override fun onCreate() {
        super.onCreate()

        // До любого LibVLC(): иначе JNI_OnLoad зовёт System.exit и роняет процесс.
        VlcNative.ensureLoaded()

        YandexAds.initialize(this) { }

        // Старый share-кэш (копии/h264) иначе копится на гигабайты.
        ShareCache.trimOnStartup(this)

        appScope.launch {
            runCatching { storageDao.migratePlaintextPasswords() }
            runCatching { storageService.backfillMissingGoogleDriveEmails() }
            runCatching { storageService.backfillMissingDropboxEmails() }
        }

        httpClient =
            OkHttpClient
                .Builder()
                // Network: Drive alt=media часто редиректит на googleusercontent —
                // application-interceptor не увидит второй hop, Bearer пропадёт.
                // Tag хранит storageId между hop'ами (header снимается в authorize).
                .addNetworkInterceptor { chain ->
                    val incoming = chain.request()
                    val storageId = incoming.header(StorageAuthProvider.HEADER_STORAGE_ID)
                        ?.toLongOrNull()
                        ?: incoming.tag(GalleryStorageTag::class.java)?.id
                    val tagged = if (storageId != null) {
                        incoming.newBuilder()
                            .tag(GalleryStorageTag::class.java, GalleryStorageTag(storageId))
                            .build()
                    } else {
                        incoming
                    }
                    val authorized = storageAuthProvider.authorize(tagged)
                    val response = chain.proceed(authorized)
                    val host = authorized.url.host
                    val driveHost = host.contains("googleapis") ||
                            host.contains("googleusercontent")
                    if (!response.isSuccessful && driveHost) {
                        val body = runCatching {
                            response.peekBody(512).string().replace('\n', ' ').take(240)
                        }.getOrDefault("")
                        Log.w(
                            TAG,
                            "Drive HTTP ${response.code} storageId=$storageId " +
                                    "hasAuth=${!authorized.header("Authorization").isNullOrBlank()} " +
                                    "url=${authorized.url} body=$body",
                        )
                    }
                    response
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
}
