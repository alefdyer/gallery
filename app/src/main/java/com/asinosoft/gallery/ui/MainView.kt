package com.asinosoft.gallery.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.Image

enum class MainViewMode {
    PHOTOS,
    ALBUMS
}

@Composable
fun MainView(
    images: List<Image>,
    albums: List<Album>,
    onImageClick: (Image) -> Unit,
    onAlbumClick: (Album) -> Unit,
) {
    var mode by rememberSaveable { mutableStateOf(MainViewMode.PHOTOS) }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter,
    ) {
        when (mode) {
            MainViewMode.PHOTOS ->
                GroupView(images, onImageClick)

            MainViewMode.ALBUMS ->
                AlbumListView(albums, onAlbumClick)
        }

        ViewModeBar(
            mode,
            onPhotos = { mode = MainViewMode.PHOTOS },
            onAlbums = { mode = MainViewMode.ALBUMS }
        )
    }
}

@Composable
fun ViewModeBar(
    mode: MainViewMode,
    onPhotos: () -> Unit,
    onAlbums: () -> Unit,
) {
    NavigationBar(
        containerColor = Color.White.copy(0.5f),
        modifier = Modifier
            .fillMaxWidth(0.5f)
            .clip(RoundedCornerShape(16.dp))
    ) {
        NavigationBarItem(
            selected = mode == MainViewMode.PHOTOS,
            onClick = onPhotos,
            icon = {
                Icon(
                    imageVector = Icons.Default.DateRange,
                    contentDescription = stringResource(id = R.string.photos)
                )
            },
            label = { stringResource(id = R.string.photos) }
        )
        NavigationBarItem(
            selected = mode == MainViewMode.ALBUMS,
            onClick = onAlbums,
            icon = {
                Icon(
                    imageVector = Icons.Default.Notifications,
                    contentDescription = stringResource(id = R.string.albums)
                )
            },
            label = { stringResource(id = R.string.albums) }
        )
    }
}
