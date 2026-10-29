package com.asinosoft.photogallery.data

private const val MAX_DURATION_MS = 12L * 60 * 60 * 1000

/**
 * Ниже этого битрейта video+audio почти не бывает — типичный признак
 * завышенной длительности у MPEG-PS (Drive / VLC / MediaMetadataRetriever).
 */
private const val MIN_PLAUSIBLE_BITRATE_BPS = 300_000L

/** Типичный MPEG-2 PS с камеры (Handycam и т.п.) — для оценки, если метаданные врут. */
private const val TYPICAL_MPEG_PS_BITRATE_BPS = 5_000_000L

/** @return durationMs или null, если значение неправдоподобно. */
fun plausibleVideoDurationMs(sizeBytes: Long, durationMs: Long): Long? {
    if (durationMs <= 0L || durationMs > MAX_DURATION_MS) return null
    if (sizeBytes <= 0L) return durationMs
    val bitrateBps = sizeBytes * 8_000L / durationMs
    return durationMs.takeIf { bitrateBps >= MIN_PLAUSIBLE_BITRATE_BPS }
}

fun looksLikeMpegProgramStream(filename: String, mimeType: String): Boolean {
    val name = filename.lowercase()
    val mime = mimeType.lowercase()
    return name.endsWith(".mpg") ||
            name.endsWith(".mpeg") ||
            name.endsWith(".vob") ||
            name.endsWith(".dat") ||
            mime == "video/mpeg" ||
            mime == "video/mp2p" ||
            mime == "video/dvd"
}

/** Грубая оценка длительности по размеру, если Drive/парсер соврали. */
fun estimateMpegPsDurationMs(sizeBytes: Long): Long? {
    if (sizeBytes <= 0L) return null
    return (sizeBytes * 8_000L / TYPICAL_MPEG_PS_BITRATE_BPS)
        .coerceAtMost(MAX_DURATION_MS)
        .takeIf { it > 0L }
}

/**
 * Лучшая длительность для UI/БД: правдоподобный замер, иначе оценка для .MPG.
 * Из нескольких правдоподобных берём меньшую (MPEG-PS чаще завышает).
 */
fun bestVideoDurationMs(
    sizeBytes: Long,
    filename: String,
    mimeType: String,
    vararg candidates: Long,
): Long {
    var best: Long? = null
    for (value in candidates) {
        val ok = plausibleVideoDurationMs(sizeBytes, value) ?: continue
        best = best?.let { minOf(it, ok) } ?: ok
    }
    if (best != null) return best
    if (looksLikeMpegProgramStream(filename, mimeType)) {
        return estimateMpegPsDurationMs(sizeBytes) ?: 0L
    }
    return 0L
}
