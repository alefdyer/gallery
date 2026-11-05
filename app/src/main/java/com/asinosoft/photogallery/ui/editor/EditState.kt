package com.asinosoft.photogallery.ui.editor

import android.net.Uri
import androidx.compose.ui.geometry.Rect

data class AdjustParams(
    val brightness: Float = 0f, // -1..1
    val contrast: Float = 0f, // -1..1
    val saturation: Float = 0f, // -1..1
    val sharpness: Float = 0f, // 0..1
)

enum class FilterPreset {
    ORIGINAL,
    MONO,
    WARM,
    COOL,
    VIVID,
    FADE,
}

data class StrokePoint(val x: Float, val y: Float)

data class DrawStroke(
    val points: List<StrokePoint>,
    val color: Long,
    val width: Float,
)

data class TextOverlay(
    val id: Long,
    val text: String,
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val color: Long = 0xFFFFFFFF,
)

data class StickerOverlay(
    val id: Long,
    val categoryId: String,
    val stickerId: String,
    val emoji: String,
    val x: Float,
    val y: Float,
    val scale: Float = 1f,
    val rotation: Float = 0f,
    val flipHorizontal: Boolean = false,
)

/**
 * Нормализованный crop в координатах показанного изображения 0..1
 * (уже после отражения и поворота). null — без обрезки.
 */
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    fun toRect() = Rect(left, top, right, bottom)

    fun isFull(): Boolean =
        left <= 0.001f && top <= 0.001f && right >= 0.999f && bottom >= 0.999f

    fun mirroredHorizontally(): CropRect = CropRect(1f - right, top, 1f - left, bottom)

    fun mirroredVertically(): CropRect = CropRect(left, 1f - bottom, right, 1f - top)

    /** Поворот содержимого на 90° против часовой стрелки. */
    fun rotatedCounterClockwise(): CropRect = CropRect(top, 1f - right, bottom, 1f - left)

    companion object {
        /** Прямоугольник по центру с заданным отношением ширины к высоте в пикселях. */
        fun centered(imageWidth: Int, imageHeight: Int, aspect: Float): CropRect {
            if (imageWidth <= 0 || imageHeight <= 0 || aspect <= 0f) return CropRect()
            val imageAspect = imageWidth.toFloat() / imageHeight
            val (width, height) = if (aspect >= imageAspect) {
                1f to (imageAspect / aspect).coerceIn(0.08f, 1f)
            } else {
                (aspect / imageAspect).coerceIn(0.08f, 1f) to 1f
            }
            val left = (1f - width) / 2f
            val top = (1f - height) / 2f
            return CropRect(left, top, left + width, top + height)
        }
    }
}

/**
 * Четыре угла перспективы в нормализованных координатах 0..1
 * (порядок: TL, TR, BR, BL). null — без warp.
 */
data class PerspectiveQuad(
    val topLeftX: Float = 0f,
    val topLeftY: Float = 0f,
    val topRightX: Float = 1f,
    val topRightY: Float = 0f,
    val bottomRightX: Float = 1f,
    val bottomRightY: Float = 1f,
    val bottomLeftX: Float = 0f,
    val bottomLeftY: Float = 1f,
) {
    fun isIdentity(): Boolean =
        topLeftX == 0f && topLeftY == 0f &&
                topRightX == 1f && topRightY == 0f &&
                bottomRightX == 1f && bottomRightY == 1f &&
                bottomLeftX == 0f && bottomLeftY == 1f
}

data class EditState(
    val rotationDegrees: Int = 0,
    val flipHorizontal: Boolean = false,
    val flipVertical: Boolean = false,
    val crop: CropRect? = null,
    val perspective: PerspectiveQuad? = null,
    val filter: FilterPreset = FilterPreset.ORIGINAL,
    val adjust: AdjustParams = AdjustParams(),
    val strokes: List<DrawStroke> = emptyList(),
    val texts: List<TextOverlay> = emptyList(),
    val stickers: List<StickerOverlay> = emptyList(),
    /** Видео: trim в мс от начала исходника. */
    val trimStartMs: Long = 0L,
    val trimEndMs: Long? = null,
    val originalVolume: Float = 1f,
    val muted: Boolean = false,
    val soundtrackUri: Uri? = null,
    val soundtrackVolume: Float = 0.8f,
) {
    val hasChanges: Boolean
        get() = this != EditState()
}
