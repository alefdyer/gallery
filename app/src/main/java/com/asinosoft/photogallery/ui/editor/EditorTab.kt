package com.asinosoft.photogallery.ui.editor

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.asinosoft.photogallery.R

enum class EditorTab(
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
    val photoOnly: Boolean = false,
    val videoOnly: Boolean = false,
) {
    TRANSFORM(R.string.editor_transform, R.drawable.editor_crop),
    FILTER(R.string.editor_filter, R.drawable.editor_filter),
    ADJUST(R.string.editor_adjust, R.drawable.editor_adjust),
    ADD(R.string.editor_add, R.drawable.editor_add),
    TRIM(R.string.editor_trim, R.drawable.editor_trim, videoOnly = true),
    SOUND(R.string.editor_sound, R.drawable.volume_up, videoOnly = true),
    ;

    companion object {
        fun forMedia(isVideo: Boolean): List<EditorTab> =
            entries.filter { tab ->
                when {
                    isVideo && tab.photoOnly -> false
                    !isVideo && tab.videoOnly -> false
                    else -> true
                }
            }
    }
}
