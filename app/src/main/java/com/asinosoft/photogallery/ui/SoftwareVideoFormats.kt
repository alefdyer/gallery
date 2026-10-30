package com.asinosoft.photogallery.ui

import com.asinosoft.photogallery.data.Media

/**
 * Контейнеры/кодеки, которые MediaCodec на типичных телефонах не декодирует
 * (MPEG-1/2, старые AVI с камеры и т.п.) — для них берём LibVLC/FFmpeg.
 *
 * Расширение файла важнее MIME: WebDAV часто врёт Content-Type (например video/mpeg для .mp4).
 */
fun needsSoftwareVideoPlayer(media: Media): Boolean {
    val name = media.filename.lowercase()
    if (isHardwareFriendlyContainer(name)) return false
    if (isLegacySoftwareContainer(name)) return true

    val mime = media.mimeType.lowercase()
    if (mime == "video/mpeg" ||
        mime == "video/mp2p" ||
        mime == "video/dvd" ||
        mime == "video/avi" ||
        mime == "video/x-msvideo" ||
        mime == "video/x-ms-wmv" ||
        mime == "video/x-flv" ||
        mime == "video/x-divx" ||
        mime == "video/3gpp" ||
        mime == "video/3gpp2"
    ) {
        return true
    }
    // Не трогаем HLS (application/vnd.apple.mpegurl) и mp4.
    if (mime.startsWith("video/") &&
        mime.contains("mpeg") &&
        !mime.contains("mp4") &&
        !mime.contains("mpegurl")
    ) {
        return true
    }
    return false
}

/** Современные контейнеры — всегда Exo/MediaCodec, даже если сервер прислал странный MIME. */
fun isHardwareFriendlyContainer(filename: String): Boolean {
    val name = filename.lowercase()
    return name.endsWith(".mp4") ||
            name.endsWith(".m4v") ||
            name.endsWith(".mov") ||
            name.endsWith(".mkv") ||
            name.endsWith(".webm")
}

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
            name.endsWith(".divx") ||
            name.endsWith(".3gp") ||
            name.endsWith(".3gpp")
}
