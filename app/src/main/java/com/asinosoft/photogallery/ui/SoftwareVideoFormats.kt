package com.asinosoft.photogallery.ui

import com.asinosoft.photogallery.data.Media

/**
 * Контейнеры/кодеки, которые MediaCodec на типичных телефонах не декодирует
 * (MPEG-1/2, старые AVI с камеры и т.п.) — для них берём LibVLC/FFmpeg.
 */
fun needsSoftwareVideoPlayer(media: Media): Boolean {
    val name = media.filename.lowercase()
    val mime = media.mimeType.lowercase()
    if (name.endsWith(".mpg") ||
        name.endsWith(".mpeg") ||
        name.endsWith(".vob") ||
        name.endsWith(".dat") ||
        name.endsWith(".avi") ||
        name.endsWith(".wmv") ||
        name.endsWith(".asf") ||
        name.endsWith(".flv") ||
        name.endsWith(".divx")
    ) {
        return true
    }
    if (mime == "video/mpeg" ||
        mime == "video/mp2p" ||
        mime == "video/dvd" ||
        mime == "video/avi" ||
        mime == "video/x-msvideo" ||
        mime == "video/x-ms-wmv" ||
        mime == "video/x-flv" ||
        mime == "video/x-divx"
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
