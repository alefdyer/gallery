package com.asinosoft.gallery

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GalleryApp : Application() {
    companion object {
        const val TAG = "gallery.app"
    }

    override fun onCreate() {
        super.onCreate()

        SingletonImageLoader.setSafe {
            ImageLoader.Builder(baseContext)
                .crossfade(true)
                .build()
        }
    }
}
