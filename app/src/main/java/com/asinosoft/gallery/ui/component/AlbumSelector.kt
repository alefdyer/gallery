package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumCategory
import com.asinosoft.gallery.data.name
import com.asinosoft.gallery.model.AlbumsViewModel

@Composable
fun AlbumSelector(
    modifier: Modifier = Modifier,
    onAlbumClick: (Album) -> Unit,
    onNewAlbumClick: (AlbumCategory) -> Unit,
    model: AlbumsViewModel = hiltViewModel()
) {
    val size = LocalWindowInfo.current.containerDpSize.width / 3
    val categories by model.albums.collectAsState(initial = listOf())

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxSize()
    ) {
        categories.forEach { category ->
            stickyHeader {
                ShadowedHeader(category.category.name())
            }

            items (category.albums) { album ->
                AlbumCover(
                    album,
                    Modifier
                        .size(size)
                        .clickable { onAlbumClick(album.album) }
                )
            }

            item {
                NewAlbumPlaceholder(
                    Modifier.size(size)
                        .clickable { onNewAlbumClick(category.category) }
                )
            }
        }
    }
}
