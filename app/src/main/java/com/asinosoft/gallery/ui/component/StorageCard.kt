package com.asinosoft.gallery.ui.component

import android.util.Log
import androidx.annotation.DrawableRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.capitalize
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.ThumbnailCache
import com.asinosoft.gallery.model.MediaViewModel
import com.asinosoft.gallery.model.StorageInfo
import kotlinx.coroutines.launch

@Composable
fun StorageCard(
    info: StorageInfo,
    onClick: () -> Unit,
) {
    val height = LocalWindowInfo.current.containerDpSize.width / 3
    Box(
        modifier = Modifier
            .height(height)
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        info.cover?.let { StorageCover(it) }

        Row(Modifier.align(Alignment.BottomStart)) {
            Box(Modifier.clip(RoundedCornerShape(50))) {
                StorageTypeIcon(type = info.storage.type)
            }

            Spacer(Modifier.width(4.dp))

            Text(
                text = info.storage.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(50))
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                Counter(R.drawable.photo, info.statistics.photoCount)
                Counter(R.drawable.videocam, info.statistics.videoCount)
            }
        }
    }
}

@Composable
private fun StorageCover(
    cover: Media,
    model: MediaViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var request by remember { mutableStateOf<ImageRequest?>(null) }

    LaunchedEffect(cover) {
        val file = ThumbnailCache.getFile(context, cover.id)
        val data = if (file.exists() && file.length() > 0) file else model.getThumbnailUri(cover)
        Log.i("view", "Storage cover = $data")
        request = ImageRequest.Builder(context)
            .data(data)
            .placeholderMemoryCacheKey("storage-cover-${cover.id}")
            .crossfade(true)
            .listener(onSuccess = { _, result ->
                if (!file.exists()) {
                    scope.launch {
                        ThumbnailCache.save(context, cover.id, result.image)
                    }
                }
            })
            .build()
    }

    Box(
        Modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
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
private fun Counter(
    @DrawableRes icon: Int,
    count: Int
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .padding(horizontal = 8.dp, vertical = 2.dp)
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
