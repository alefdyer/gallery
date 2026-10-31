package com.asinosoft.photogallery.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import coil3.compose.rememberAsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.AlbumWithCover
import com.asinosoft.photogallery.data.EXTERNAL_STORAGE_ID
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.ThumbnailCache
import com.asinosoft.photogallery.data.storage.StorageType
import com.asinosoft.photogallery.model.MediaViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val LOCAL_STORAGE_ID = 1L

@Composable
fun AlbumCover(
    album: AlbumWithCover,
    modifier: Modifier = Modifier,
) {
    val photoCount = album.album.photoCount
    val videoCount = album.album.videoCount
    val hasSplit = photoCount > 0 || videoCount > 0

    Box(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AlbumThumbnail(album.cover, Modifier.fillMaxSize())

        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.7f)
                    )
                )
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = album.album.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (hasSplit) {
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
            } else if (album.album.count > 0) {
                // До обновления stats после миграции.
                Text(
                    text = "${album.album.count}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun CoverCounter(
    @DrawableRes icon: Int,
    count: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier.size(12.dp)
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.9f),
            maxLines = 1
        )
    }
}

@Composable
private fun AlbumThumbnail(
    cover: Media?,
    modifier: Modifier = Modifier,
    model: MediaViewModel = hiltViewModel(),
) {
    if (null == cover) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.album),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
        }
        return
    }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val cacheKey = remember(cover.id) { "media-${cover.id}" }
    val cacheFile = remember(cover.id) { ThumbnailCache.getFile(context, cover.id) }
    var cached by remember(cover.id) {
        mutableStateOf(cacheFile.exists() && cacheFile.length() > 0)
    }
    val isRemoteVideo = cover.video != null &&
            LOCAL_STORAGE_ID != cover.storageId &&
            EXTERNAL_STORAGE_ID != cover.storageId
    val hasNativeThumb = cover.thumbnail != null
    val useStoredThumb =
        hasNativeThumb && cover.storageType != StorageType.GOOGLE_DRIVE

    LaunchedEffect(cover.id, cached) {
        if (cached) return@LaunchedEffect
        repeat(80) {
            if (!isActive) return@LaunchedEffect
            if (cacheFile.exists() && cacheFile.length() > 0) {
                cached = true
                return@LaunchedEffect
            }
            delay(750)
        }
    }

    val immediateData: Any? = remember(cover, cached, useStoredThumb) {
        when {
            cached -> cacheFile
            isRemoteVideo && !hasNativeThumb && cover.storageType != StorageType.GOOGLE_DRIVE -> null
            useStoredThumb -> cover.thumbnail
            LOCAL_STORAGE_ID == cover.storageId || EXTERNAL_STORAGE_ID == cover.storageId -> cover.uri
            else -> null
        }
    }
    val remoteData by produceState<Any?>(
        null,
        cover,
        immediateData,
        isRemoteVideo,
        hasNativeThumb
    ) {
        if (null != immediateData) {
            value = null
            return@produceState
        }
        val uri = model.getThumbnailUri(cover)
        value =
            if (isRemoteVideo && !hasNativeThumb && cover.storageType != StorageType.GOOGLE_DRIVE) {
            uri.takeUnless { it.scheme == "http" || it.scheme == "https" }
        } else {
                uri.takeUnless { it.scheme == "gdrive-thumb-missing" || it.scheme == "dropbox-thumb-missing" }
        }
    }
    val data = immediateData ?: remoteData

    val request = remember(data, cacheKey, cover.id) {
        if (null == data) return@remember null
        ImageRequest.Builder(context)
            .data(data)
            .size(300, 300)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .placeholderMemoryCacheKey(cacheKey)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(CachePolicy.ENABLED)
            .allowHardware(true)
            .listener(onSuccess = { _, result ->
                if (!cacheFile.exists()) {
                    scope.launch {
                        ThumbnailCache.save(context, cover.id, result.image)
                    }
                }
            })
            .build()
    }

    if (null == request) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Icon(
                painter = painterResource(R.drawable.album),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(40.dp)
            )
        }
    } else {
        Image(
            painter = rememberAsyncImagePainter(model = request),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    }
}
