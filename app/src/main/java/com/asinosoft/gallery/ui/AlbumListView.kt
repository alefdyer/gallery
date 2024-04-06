package com.asinosoft.gallery.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.ui.theme.GalleryTheme

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
                    .clip(RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                AsyncImage(
                    model = album.cover,
                    contentDescription = "",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable { onAlbumClick(album) }
                )

                Text(
                    text = album.name,
                    textAlign = TextAlign.Center,
                    overflow = TextOverflow.Ellipsis,
                    maxLines = 1,
                    color = Color.White,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.4f))
                        .fillMaxWidth()
                        .padding(start = 8.dp)
                )
            }
        }
    }
}

@Preview
@Composable
fun AlbumListViewPreview() {
    GalleryTheme() {
        AlbumListView(albums = listOf(
            Album("Лу Синь в юности", 123, 1234567890, ""),
            Album("Журавль Красавка и Серый волк в сапогах", 123, 1234567890, ""),
            Album("Chicago Pizza", 98, 3456, "")
        )) {
        }
    }
}