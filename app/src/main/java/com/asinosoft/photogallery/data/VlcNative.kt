package com.asinosoft.photogallery.data

import android.os.Looper
import android.util.Log

/**
 * Грузит libvlcjni до первого обращения к классу [org.videolan.libvlc.LibVLC].
 * Статический блок LibVLC при ошибке JNI вызывает System.exit(1) и убивает процесс.
 * Свой loadLibrary только бросает UnsatisfiedLinkError — его можно пережить.
 */
object VlcNative {
    private const val TAG = "VlcNative"
    private const val TRACK_CLASS = "org.videolan.libvlc.interfaces.IMedia\$Track"

    private val lock = Any()

    @Volatile
    private var loaded: Boolean? = null

    /** Только с главного потока: FindClass в JNI_OnLoad иначе не видит классы приложения. */
    fun ensureLoaded(): Boolean {
        loaded?.let { return it }
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.e(TAG, "LibVLC preload skipped: not the main thread")
            return false
        }
        return synchronized(lock) {
            loaded?.let { return it }
            val ok = runCatching {
                val loader = VlcNative::class.java.classLoader
                Class.forName(TRACK_CLASS, true, loader)
                System.loadLibrary("c++_shared")
                System.loadLibrary("vlc")
                System.loadLibrary("vlcjni")
            }.onFailure { error ->
                Log.e(TAG, "LibVLC native load failed", error)
            }.isSuccess
            loaded = ok
            if (ok) Log.i(TAG, "LibVLC native libraries loaded")
            ok
        }
    }
}
