package com.asinosoft.photogallery.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.AlbumWithCover
import java.io.File

@Composable
fun PersonAlbumCover(
    album: AlbumWithCover,
    displayName: String?,
    faceThumbPath: String?,
    modifier: Modifier = Modifier,
) {
    val photoCount = album.album.photoCount
    val videoCount = album.album.videoCount
    val title = displayName?.takeIf { it.isNotBlank() }
    val context = LocalContext.current
    val faceFile = remember(faceThumbPath) {
        faceThumbPath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0 }
    }

    Box(
        modifier
            .clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (faceFile != null) {
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(faceFile)
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            // Fallback, пока нет кропа лица
            AlbumThumbnail(album.cover, Modifier.fillMaxSize())
        }

        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.72f)
                    )
                )
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {

            if (title != null) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                if (photoCount > 0) {
                    CoverCounter(R.drawable.photo, photoCount)
                }
                if (videoCount > 0) {
                    CoverCounter(R.drawable.videocam, videoCount)
                }
            }
        }
    }
}
