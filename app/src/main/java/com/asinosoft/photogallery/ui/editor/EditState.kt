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
 * Нормализованный crop в координатах исходного изображения 0..1.
 * null — без обрезки.
 */
data class CropRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    fun toRect() = Rect(left, top, right, bottom)
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
