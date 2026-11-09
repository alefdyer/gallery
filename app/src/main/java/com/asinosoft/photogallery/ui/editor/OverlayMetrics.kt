package com.asinosoft.photogallery.ui.editor

/**
 * Размеры/позиции оверлеев — в координатах **кадра фото** (не letterbox-контейнера),
 * чтобы превью и JPEG совпадали.
 *
 * 36.sp / 360.dp ≈ 0.10 — прежний вид стикера на типичной ширине превью.
 * 22.sp / 360.dp ≈ 0.061 — текст: одна формула для Compose и Canvas TextPaint.
 */
const val STICKER_SIZE_FRACTION = 0.10f
const val STICKER_BOX_FRACTION = 0.155f
const val TEXT_SIZE_FRACTION = 0.061f

/** Видимый кадр фото внутри editor box (letterbox left/top + размер). */
data class OverlayContentFrame(
    val left: Float,
    val top: Float,
    val width: Float,
    val height: Float,
)

fun stickerTextSizePx(imageWidthPx: Float, scale: Float): Float =
    (imageWidthPx * STICKER_SIZE_FRACTION * scale).coerceAtLeast(1f)

fun stickerBoxSizePx(imageWidthPx: Float, scale: Float): Float =
    (imageWidthPx * STICKER_BOX_FRACTION * scale).coerceAtLeast(stickerTextSizePx(imageWidthPx, scale))

/** Размер текста в px ширины кадра — и превью (.toSp()), и JPEG (Paint.textSize). */
fun textOverlaySizePx(imageWidthPx: Float, scale: Float): Float =
    (imageWidthPx * TEXT_SIZE_FRACTION * scale).coerceAtLeast(1f)
