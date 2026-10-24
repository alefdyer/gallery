package com.asinosoft.photogallery.ui.component

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Настройки просмотра: автозапуск и звук видео. */
object GallerySettings {
    private const val PREFS = "gallery_settings"
    private const val KEY_AUTOPLAY = "autoplay_video"
    private const val KEY_SOUND = "video_sound"

    private val _autoplayVideo = MutableStateFlow(true)
    private val _videoSound = MutableStateFlow(false)

    val autoplayVideo: StateFlow<Boolean> = _autoplayVideo.asStateFlow()
    val videoSound: StateFlow<Boolean> = _videoSound.asStateFlow()

    @Volatile
    private var ready = false

    fun ensureLoaded(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val prefs = prefs(context)
            _autoplayVideo.value = prefs.getBoolean(KEY_AUTOPLAY, true)
            _videoSound.value = prefs.getBoolean(KEY_SOUND, false)
            ready = true
        }
    }

    fun setAutoplayVideo(context: Context, enabled: Boolean) {
        if (enabled == _autoplayVideo.value && ready) return
        ensureLoaded(context)
        _autoplayVideo.value = enabled
        prefs(context).edit { putBoolean(KEY_AUTOPLAY, enabled) }
    }

    fun setVideoSound(context: Context, enabled: Boolean) {
        if (enabled == _videoSound.value && ready) return
        ensureLoaded(context)
        _videoSound.value = enabled
        prefs(context).edit { putBoolean(KEY_SOUND, enabled) }
    }

    private fun prefs(context: Context): SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

@Composable
fun rememberAutoplayVideo(): Boolean {
    val context = LocalContext.current
    remember { GallerySettings.ensureLoaded(context) }
    return GallerySettings.autoplayVideo.collectAsState().value
}

@Composable
fun rememberVideoSound(): Boolean {
    val context = LocalContext.current
    remember { GallerySettings.ensureLoaded(context) }
    return GallerySettings.videoSound.collectAsState().value
}
