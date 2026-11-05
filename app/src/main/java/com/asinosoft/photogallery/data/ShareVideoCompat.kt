package com.asinosoft.photogallery.data

import android.content.Context
import android.util.Log
import com.asinosoft.photogallery.R
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * Конвертация только когда мессенджер файл «как есть» не примет:
 * .MPG/.AVI/.WMV/… → LibVLC/FFmpeg в H.264 MP4.
 *
 * Камера/телефон (.mp4/.mov с H.264 или HEVC) — без конверта, напрямую.
 */
object ShareVideoCompat {
    private const val TAG = "ShareVideoCompat"

    fun isLegacySoftwareContainer(filename: String): Boolean {
        val name = filename.lowercase()
        return name.endsWith(".mpg") ||
                name.endsWith(".mpeg") ||
                name.endsWith(".vob") ||
                name.endsWith(".dat") ||
                name.endsWith(".avi") ||
                name.endsWith(".wmv") ||
                name.endsWith(".asf") ||
                name.endsWith(".flv") ||
                name.endsWith(".divx")
    }

    fun needsTranscodeForMessenger(filename: String): Boolean =
        isLegacySoftwareContainer(filename)

    fun needsTranscodeForMessenger(file: File): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        return isLegacySoftwareContainer(file.name)
    }

    /**
     * @return файл+MIME для шаринга, или null если конвертация обязательна и не удалась.
     */
    suspend fun ensureMessengerCompatible(
        context: Context,
        input: File,
    ): Pair<File, String>? {
        if (!needsTranscodeForMessenger(input)) {
            return input to guessMime(input)
        }
        val outDir = File(context.cacheDir, "share/transcoded").also { it.mkdirs() }
        val out = File(outDir, "${input.nameWithoutExtension}_${input.length()}_h264_v2.mp4")
        if (out.exists() &&
            out.length() > MIN_OUTPUT_BYTES &&
            out.lastModified() >= input.lastModified()
        ) {
            return out to "video/mp4"
        }
        ShareUi.setStatus(context.getString(R.string.share_converting), progress = 0f)
        out.delete()
        val ok = transcodeWithLibVlc(context, input, out)
        if (!ok || !out.exists() || out.length() < MIN_OUTPUT_BYTES) {
            out.delete()
            Log.w(TAG, "transcode failed for ${input.name}")
            return null
        }
        ShareUi.setProgress(1f)
        return out to "video/mp4"
    }

    private suspend fun transcodeWithLibVlc(
        context: Context,
        input: File,
        output: File,
    ): Boolean = withContext(Dispatchers.Main) {
        if (!VlcNative.ensureLoaded()) {
            Log.w(TAG, "LibVLC native load failed, skip transcode for ${input.name}")
            return@withContext false
        }
        val inputBytes = input.length().coerceAtLeast(1L)
        val progressJob = launch(Dispatchers.IO) {
            var last = -1
            while (true) {
                kotlinx.coroutines.delay(250L)
                val written = output.length().coerceAtLeast(0L)
                val pct = ((written * 100L) / (inputBytes / 2).coerceAtLeast(1L))
                    .toInt()
                    .coerceIn(0, 99)
                if (pct != last) {
                    last = pct
                    ShareUi.setProgress(pct / 100f)
                }
            }
        }
        try {
            suspendCancellableCoroutine { cont ->
                val done = AtomicBoolean(false)
                val options = arrayListOf(
                    "--avcodec-hw=none",
                    "--no-audio-time-stretch",
                    "--no-video-title-show",
                )
                val lib = LibVLC(context.applicationContext, options)
                val player = MediaPlayer(lib)
                val dst = output.absolutePath.replace('\\', '/')
                val escaped = dst.replace("'", "\\'")
                val sout =
                    ":sout=#transcode{" +
                            "vcodec=h264,vb=1500,fps=25," +
                            "acodec=mp4a,ab=128,channels=2,samplerate=44100" +
                            "}:std{access=file,mux=mp4{faststart},dst='$escaped'}"

                fun releasePlayer() {
                    runCatching { player.setEventListener(null) }
                    runCatching { player.stop() }
                    runCatching { player.release() }
                    runCatching { lib.release() }
                }

                fun finish(success: Boolean) {
                    if (!done.compareAndSet(false, true)) return
                    releasePlayer()
                    val ok = success && output.exists() && output.length() >= MIN_OUTPUT_BYTES
                    if (cont.isActive) cont.resume(ok)
                }

                player.setEventListener { event ->
                    when (event.type) {
                        MediaPlayer.Event.EndReached -> {
                            kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                                kotlinx.coroutines.delay(FASTSTART_FLUSH_MS)
                                finish(true)
                            }
                        }

                        MediaPlayer.Event.EncounteredError -> finish(false)
                    }
                }

                cont.invokeOnCancellation {
                    if (done.compareAndSet(false, true)) releasePlayer()
                }

                runCatching {
                    val media = Media(lib, input.absolutePath)
                    media.addOption(sout)
                    media.addOption(":sout-all")
                    media.addOption(":sout-keep")
                    player.media = media
                    media.release()
                    player.play()
                }.onFailure {
                    Log.w(TAG, "LibVLC sout start failed", it)
                    finish(false)
                }
            }
        } finally {
            progressJob.cancel()
        }
    }

    private fun guessMime(file: File): String {
        val name = file.name.lowercase()
        return when {
            name.endsWith(".mp4") || name.endsWith(".m4v") -> "video/mp4"
            name.endsWith(".mov") -> "video/quicktime"
            name.endsWith(".webm") -> "video/webm"
            name.endsWith(".mkv") -> "video/x-matroska"
            name.endsWith(".avi") -> "video/x-msvideo"
            name.endsWith(".mpg") || name.endsWith(".mpeg") -> "video/mpeg"
            name.endsWith(".3gp") -> "video/3gpp"
            else -> "video/*"
        }
    }

    private const val MIN_OUTPUT_BYTES = 1024L
    private const val FASTSTART_FLUSH_MS = 800L
}
