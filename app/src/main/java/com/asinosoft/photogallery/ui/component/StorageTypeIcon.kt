package com.asinosoft.photogallery.ui.component

import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.storage.StorageType

/**
 * Та же маска, что у adaptive-иконок приложений (на Samsung — One UI squircle).
 * Viewport маски — 100×100.
 */
val SystemAppIconShape: Shape = object : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val mask = AdaptiveIconDrawable(
            ColorDrawable(android.graphics.Color.TRANSPARENT),
            ColorDrawable(android.graphics.Color.TRANSPARENT),
        ).iconMask
        val scaled = android.graphics.Path(mask)
        val matrix = android.graphics.Matrix().apply {
            setScale(size.width / 100f, size.height / 100f)
        }
        scaled.transform(matrix)
        return Outline.Generic(scaled.asComposePath())
    }
}

fun StorageType.drawableRes(): Int = when (this) {
    StorageType.LOCAL -> R.drawable.smartphone
    StorageType.DROPBOX -> R.drawable.dropbox
    StorageType.NEXTCLOUD -> R.drawable.nextcloud
    StorageType.WEBDAV -> R.drawable.webdav
    StorageType.YANDEX -> R.drawable.yandex_disk
    StorageType.GOOGLE_DRIVE -> R.drawable.google_drive
    StorageType.ONEDRIVE -> R.drawable.onedrive
}

@Composable
fun StorageTypeIcon(type: StorageType, modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(type.drawableRes()),
        contentDescription = null,
        modifier = modifier
            .size(32.dp)
            .clip(SystemAppIconShape)
    )
}
