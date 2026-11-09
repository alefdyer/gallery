package com.asinosoft.photogallery.ui.component

import android.text.format.Formatter
import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.ThumbnailCache

private val textShadow = Shadow(color = Color.Black.copy(alpha = 0.5f), blurRadius = 8f)
private val MIN_TITLE_SIZE = 14.sp

@Composable
fun ImageListHeaderBackground(
    cover: Media,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val heavyReady = LocalAlbumHeavyContentReady.current
    val request = remember(cover, context, heavyReady) {
        val file = ThumbnailCache.getFile(context, cover.id)
        val cached = file.exists() && file.length() > 0
        // Видео-обложка: не декодируем кадр из uri, пока альбом ещё «лёгкий».
        val data: Any? = when {
            cached -> file
            cover.video != null && !heavyReady -> null
            else -> cover.uri ?: file.takeIf { it.exists() }
        }
        if (null == data) return@remember null
        ImageRequest.Builder(context)
            .data(data)
            .placeholderMemoryCacheKey(thumbnailCacheKey(cover))
            .memoryCacheKey(thumbnailCacheKey(cover))
            .diskCacheKey(thumbnailCacheKey(cover))
            .crossfade(true)
            .build()
    }

    Box(modifier.clipToBounds()) {
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.3f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.75f)
                    )
                )
        )
    }
}

@Composable
fun ImageListHeaderInfo(
    title: String,
    photoCount: Int,
    videoCount: Int,
    modifier: Modifier = Modifier,
    photosEnabled: Boolean = true,
    videosEnabled: Boolean = true,
    sizeBytes: Long? = null,
    subtitle: String? = null,
    @DrawableRes subtitleIcon: Int = R.drawable.folder,
    subtitleIconTinted: Boolean = true,
    onPhotosClick: (() -> Unit)? = null,
    onVideosClick: (() -> Unit)? = null
) {
    Box(modifier) {
        Row(
            // Bottom, а не CenterVertically: правая колонка выросла (размер над счётчиками),
            // и заголовок уезжал вверх.
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
        ) {
            val style = MaterialTheme.typography.headlineSmall
            Column(modifier = Modifier.weight(1f)) {
                if (!subtitle.isNullOrBlank()) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Image(
                            painter = painterResource(subtitleIcon),
                            contentDescription = null,
                            colorFilter = if (subtitleIconTinted) {
                                ColorFilter.tint(Color.White.copy(alpha = 0.85f))
                            } else {
                                null
                            },
                            modifier = Modifier
                                .size(18.dp)
                                .clip(SystemAppIconShape)
                        )
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.titleMedium.copy(shadow = textShadow),
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Text(
                    text = title,
                    style = style.copy(shadow = textShadow),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    autoSize = TextAutoSize.StepBased(
                        minFontSize = MIN_TITLE_SIZE,
                        maxFontSize = style.fontSize
                    )
                )
            }

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TotalSizeLabel(sizeBytes)
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Counter(R.drawable.photo, photoCount, photosEnabled, onPhotosClick)
                    Counter(R.drawable.videocam, videoCount, videosEnabled, onVideosClick)
                }
            }
        }
    }
}

/** Общий размер над счётчиками в шапке */
@Composable
private fun TotalSizeLabel(sizeBytes: Long?) {
    if (null == sizeBytes || sizeBytes <= 0) return
    Text(
        text = Formatter.formatShortFileSize(LocalContext.current, sizeBytes),
        style = MaterialTheme.typography.labelSmall.copy(shadow = textShadow),
        fontWeight = FontWeight.SemiBold,
        color = Color.White,
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
    )
}

@Composable
fun ImageListHeaderTitle(
    title: String,
    @DrawableRes icon: Int,
    count: Int,
    modifier: Modifier = Modifier,
    sizeBytes: Long? = null
) {
    Box(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall.copy(shadow = textShadow),
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                TotalSizeLabel(sizeBytes)
                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                        .padding(horizontal = 4.dp, vertical = 2.dp)
                ) {
                    Counter(icon, count, enabled = true, onClick = null)
                }
            }
        }
    }
}

@Composable
private fun Counter(
    @DrawableRes icon: Int,
    count: Int,
    enabled: Boolean,
    onClick: (() -> Unit)?
) {
    val alpha by animateFloatAsState(if (enabled) 1f else 0.4f, label = "counterAlpha")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .let { if (null == onClick) it else it.clickable(onClick = onClick) }
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .alpha(alpha)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = Color.White
        )
    }
}
