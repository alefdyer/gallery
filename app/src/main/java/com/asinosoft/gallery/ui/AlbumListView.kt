package com.asinosoft.gallery.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.ui.theme.Typography

@Composable
fun AlbumListView(
    albums: List<Album>,
    onAlbumClick: (Album) -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
    ) {
        items(albums) { album ->
            Box(
                modifier = Modifier
                    .padding(1.dp)
                    .clip(RoundedCornerShape(12.dp))
            ) {
                AsyncImage(
                    model = album.cover,
                    contentDescription = "",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onAlbumClick(album) }
                )

                AlbumImages(album)

                AlbumInfo(album)
            }
        }
    }
}

@Composable
fun BoxScope.AlbumImages(album: Album) {
    Text(
        text = " ${album.count} ",
        color = Color.White,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color.Transparent.copy(0.5f),
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
fun BoxScope.AlbumInfo(album: Album) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.4f))
            .align(Alignment.BottomCenter)
    ) {
        val size = Formatter.formatShortFileSize(LocalContext.current, album.size)

        Text(
            text = album.name,
            overflow = TextOverflow.Ellipsis,
            maxLines = 1,
            color = Color.White,
            modifier = Modifier
                .padding(start = 8.dp)
                .fillMaxWidth()
                .weight(1f)
        )

        Text(
            text = size,
            style = Typography.bodySmall,
            color = Color.White,
            maxLines = 1,
            modifier = Modifier
                .padding(end = 8.dp)
        )
    }
}
