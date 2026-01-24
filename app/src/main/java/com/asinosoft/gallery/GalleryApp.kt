package com.asinosoft.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.Dispatchers

@HiltAndroidApp
class GalleryApp : Application() {
    companion object {
        const val TAG = "gallery.app"
    }

    override fun onCreate() {
        super.onCreate()

        SingletonImageLoader.setSafe {
            ImageLoader
                .Builder(baseContext)
                .crossfade(true)
                .coroutineContext(Dispatchers.IO)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .memoryCache(
                    MemoryCache
                        .Builder()
                        .maxSizePercent(this, 0.25)
                        .build(),
                ).build()
        }
    }
}
