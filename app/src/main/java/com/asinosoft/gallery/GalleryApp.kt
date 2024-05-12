package com.asinosoft.gallery

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class GalleryApp : Application() {
    companion object {
        const val TAG = "gallery.app"
    }
}
