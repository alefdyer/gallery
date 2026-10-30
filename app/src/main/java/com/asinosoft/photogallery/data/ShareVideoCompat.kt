package com.asinosoft.photogallery.data

import android.content.Context
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer

/**
 * Мессенджеры (Telegram/WhatsApp) часто показывают чёрный экран + звук
 * на HEVC/MPEG — им нужен H.264 + AAC в MP4.
 *
 * HEVC и др. с MediaCodec → Media3 Transformer.
 * MPEG/.MPG/.AVI без MediaCodec → LibVLC (FFmpeg) transcode.
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

    fun needsTranscodeForMessenger(file: File): Boolean {
        if (!file.exists() || file.length() <= 0L) return false
        if (isLegacySoftwareContainer(file.name)) return true
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            var hasVideo = false
            var h264 = false
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i)
                    .getString(MediaFormat.KEY_MIME)
                    ?.lowercase()
                    ?: continue
                if (!mime.startsWith("video/")) continue
                hasVideo = true
                if (mime == MimeTypes.VIDEO_H264 || mime == "video/avc") {
                    h264 = true
                }
            }
            hasVideo && !h264
        } catch (ex: Throwable) {
            Log.w(TAG, "probe failed ${file.name}", ex)
            val name = file.name.lowercase()
            name.endsWith(".mov") || name.endsWith(".mkv")
        } finally {
            runCatching { extractor.release() }
        }
    }

    /**
     * @return файл+MIME для шаринга, или null если конвертация обязательна и не удалась
     * (сырой .MPG в WhatsApp слать нельзя).
     */
    suspend fun ensureMessengerCompatible(
        context: Context,
        input: File,
        onProgressMessage: () -> Unit,
    ): Pair<File, String>? {
        if (!needsTranscodeForMessenger(input)) {
            return input to guessMime(input)
        }
        val outDir = File(context.cacheDir, "share/transcoded").also { it.mkdirs() }
        // v2: faststart+baseline — старый кэш без moov в начале для MAX не годится.
        val out = File(outDir, "${input.nameWithoutExtension}_${input.length()}_h264_v2.mp4")
        if (out.exists() &&
            out.length() > MIN_OUTPUT_BYTES &&
            out.lastModified() >= input.lastModified()
        ) {
            return out to "video/mp4"
        }
        onProgressMessage()
        out.delete()
        val legacy = isLegacySoftwareContainer(input.name)
        val ok = if (legacy) {
            transcodeWithLibVlc(context, input, out)
        } else {
            transcodeWithTransformer(context, input, out)
        }
        if (!ok || !out.exists() || out.length() < MIN_OUTPUT_BYTES) {
            out.delete()
            Log.w(TAG, "transcode failed for ${input.name} legacy=$legacy")
            if (legacy) return null
            // HEVC: в крайнем случае отдаём оригинал (иногда WA всё же примет).
            return input to guessMime(input)
        }
        return out to "video/mp4"
    }

    private suspend fun transcodeWithTransformer(
        context: Context,
        input: File,
        output: File,
    ): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            val transformer = Transformer.Builder(context.applicationContext)
                .setVideoMimeType(MimeTypes.VIDEO_H264)
                .setAudioMimeType(MimeTypes.AUDIO_AAC)
                .addListener(
                    object : Transformer.Listener {
                        override fun onCompleted(
                            composition: Composition,
                            exportResult: ExportResult,
                        ) {
                            if (cont.isActive) cont.resume(true)
                        }

                        override fun onError(
                            composition: Composition,
                            exportResult: ExportResult,
                            exportException: ExportException,
                        ) {
                            Log.w(TAG, "Transformer error", exportException)
                            if (cont.isActive) cont.resume(false)
                        }
                    },
                )
                .build()

            cont.invokeOnCancellation {
                runCatching { transformer.cancel() }
            }

            val edited = EditedMediaItem.Builder(
                MediaItem.fromUri(Uri.fromFile(input)),
            ).build()
            runCatching {
                transformer.start(edited, output.absolutePath)
            }.onFailure {
                Log.w(TAG, "Transformer start failed", it)
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    /**
     * MPEG-1/2 и старые AVI декодирует только FFmpeg внутри LibVLC.
     * sout → H.264 + AAC в MP4 без отрисовки на экран.
     */
    private suspend fun transcodeWithLibVlc(
        context: Context,
        input: File,
        output: File,
    ): Boolean = LibVlcCoordinator.withExclusive {
        withContext(Dispatchers.Main) {
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
                // faststart — moov в начале (MAX/Telegram иначе первые секунды чёрные).
                // Без venc=x264{...}: на Android LibVLC часто нет x264 и бывает native crash.
                val sout =
                    ":sout=#transcode{" +
                            "vcodec=h264,vb=1500,fps=25," +
                            "acodec=mp4a,ab=128,channels=2,samplerate=44100" +
                            "}:std{access=file,mux=mp4{faststart},dst='$escaped'}"

                fun finish(success: Boolean) {
                    if (!done.compareAndSet(false, true)) return
                    runCatching { player.setEventListener(null) }
                    runCatching { player.stop() }
                    runCatching { player.release() }
                    runCatching { lib.release() }
                    val ok = success && output.exists() && output.length() >= MIN_OUTPUT_BYTES
                    if (cont.isActive) cont.resume(ok)
                }

                player.setEventListener { event ->
                    when (event.type) {
                        MediaPlayer.Event.EndReached -> {
                            Log.i(TAG, "LibVLC sout EndReached size=${output.length()}")
                            finish(true)
                        }

                        MediaPlayer.Event.EncounteredError -> {
                            Log.w(TAG, "LibVLC sout EncounteredError")
                            finish(false)
                        }
                    }
                }

                cont.invokeOnCancellation {
                    if (done.compareAndSet(false, true)) {
                        runCatching { player.setEventListener(null) }
                        runCatching { player.stop() }
                        runCatching { player.release() }
                        runCatching { lib.release() }
                    }
                }

                runCatching {
                    val media = Media(lib, input.absolutePath)
                    media.addOption(sout)
                    media.addOption(":sout-all")
                    media.addOption(":sout-keep")
                    player.media = media
                    media.release()
                    Log.i(TAG, "LibVLC sout start ${input.name} → ${output.name}")
                    player.play()
                }.onFailure {
                    Log.w(TAG, "LibVLC sout start failed", it)
                    finish(false)
                }
            }
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
}
