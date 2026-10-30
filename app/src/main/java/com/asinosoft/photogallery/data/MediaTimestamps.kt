package com.asinosoft.photogallery.data

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.regex.Pattern

/** Съёмка → изменение → создание; для сортировки сетки. */
fun mediaSortDateTime(
    takenAt: Long?,
    modifiedAt: Long?,
    createdAt: Long?,
    zone: ZoneId = ZoneId.systemDefault(),
): Pair<LocalDate, LocalTime> {
    val ms = positiveEpoch(takenAt)
        ?: positiveEpoch(modifiedAt)
        ?: positiveEpoch(createdAt)
        ?: System.currentTimeMillis()
    val dt = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()
    return dt.toLocalDate() to dt.toLocalTime()
}

fun positiveEpoch(ms: Long?): Long? = ms?.takeIf { it > 0L }

fun Date?.toEpochMillis(): Long? = this?.time?.takeIf { it > 0L }

/**
 * RFC 3339, ISO local, или EXIF `yyyy:MM:dd HH:mm:ss` (Drive imageMediaMetadata.time).
 */
fun parseRfc3339Millis(value: String?): Long? = parseMediaDateMillis(value)

fun parseMediaDateMillis(
    value: String?,
    zone: ZoneId = ZoneId.systemDefault(),
): Long? {
    if (value.isNullOrBlank()) return null
    val trimmed = value.trim()
    runCatching {
        return OffsetDateTime.parse(trimmed).toInstant().toEpochMilli().takeIf { it > 0L }
    }
    runCatching {
        return LocalDateTime.parse(trimmed).atZone(zone).toInstant().toEpochMilli()
            .takeIf { it > 0L }
    }
    runCatching {
        return LocalDateTime.parse(trimmed, EXIF_DATE_TIME)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
            .takeIf { it > 0L }
    }
    runCatching {
        return LocalDateTime.parse(trimmed, EXIF_DATE_TIME_MIN)
            .atZone(zone)
            .toInstant()
            .toEpochMilli()
            .takeIf { it > 0L }
    }
    return null
}

fun epochToLocalDateTime(
    ms: Long,
    zone: ZoneId = ZoneId.systemDefault(),
) = Instant.ofEpochMilli(ms).atZone(zone).toLocalDateTime()

/** date+time из БД → epoch (старые строки до takenAt/modifiedAt). */
fun Media.sortDateTimeEpochMillis(zone: ZoneId = ZoneId.systemDefault()): Long =
    date.atTime(time).atZone(zone).toInstant().toEpochMilli()

/**
 * Дата съёмки из имени: `20260924_141005.jpg`, `IMG_20260924_141005`,
 * `2026-09-24_14-10-05.jpg`.
 */
fun parseTakenAtFromFilename(
    filename: String?,
    zone: ZoneId = ZoneId.systemDefault(),
): Long? {
    if (filename.isNullOrBlank()) return null
    val base = filename.substringAfterLast('/').substringBeforeLast('.')
    val compact = FILENAME_COMPACT_DT.matcher(base)
    if (compact.find()) {
        val g = compact.group()
        val digits = g.filter { it.isDigit() }
        if (digits.length >= 14) {
            return runCatching {
                LocalDateTime.parse(digits.take(14), FILENAME_YMD_HMS)
                    .atZone(zone)
                    .toInstant()
                    .toEpochMilli()
            }.getOrNull()?.takeIf { it > 0L }
        }
    }
    val dashed = FILENAME_DASHED_DT.matcher(base)
    if (dashed.find()) {
        val y = dashed.group(1)
        val mo = dashed.group(2)
        val d = dashed.group(3)
        val h = dashed.group(4)
        val mi = dashed.group(5)
        val s = dashed.group(6)
        return runCatching {
            LocalDateTime.parse("$y$mo$d$h$mi$s", FILENAME_YMD_HMS)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        }.getOrNull()?.takeIf { it > 0L }
    }
    return null
}

private val EXIF_DATE_TIME = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")
private val EXIF_DATE_TIME_MIN = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm")
private val FILENAME_YMD_HMS = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

private val FILENAME_COMPACT_DT: Pattern =
    Pattern.compile(
        "(?<!\\d)(20\\d{2}|19\\d{2})(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])[_-]?" +
                "([01]\\d|2[0-3])([0-5]\\d)([0-5]\\d)(?!\\d)"
    )

private val FILENAME_DASHED_DT: Pattern =
    Pattern.compile(
        "(?<!\\d)(20\\d{2}|19\\d{2})-(0[1-9]|1[0-2])-(0[1-9]|[12]\\d|3[01])[ _]" +
                "([01]\\d|2[0-3])[:-]([0-5]\\d)[:-]([0-5]\\d)(?!\\d)"
    )
