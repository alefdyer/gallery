package com.asinosoft.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.svg.SvgDecoder
import com.asinosoft.gallery.data.storage.StorageAuthProvider
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient

@HiltAndroidApp
class GalleryApp : Application() {
    @Inject lateinit var storageAuthProvider: StorageAuthProvider

    lateinit var httpClient: OkHttpClient
        private set

    companion object {
        const val TAG = "gallery.app"
    }

    override fun onCreate() {
        super.onCreate()
        httpClient =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    chain.proceed(storageAuthProvider.authorize(chain.request()))
                }.build()

        SingletonImageLoader.setSafe {
            ImageLoader
                .Builder(baseContext)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .memoryCache(
                    MemoryCache
                        .Builder()
                        .maxSizePercent(this, 0.35)
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
                    add(OkHttpNetworkFetcherFactory({ httpClient }))
                }.build()
        }
    }
}
