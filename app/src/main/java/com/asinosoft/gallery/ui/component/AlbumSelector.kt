package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
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
    val size = LocalWindowInfo.current.containerDpSize.width / 3.5f
    val categories by model.albums.collectAsState(initial = listOf())

    Column(modifier.verticalScroll(rememberScrollState())) {
        categories.forEach { category ->
            Column {
                Text(
                    text = category.category.name(),
                    style = MaterialTheme.typography.headlineMedium,
                    modifier = Modifier.padding(4.dp)
                )

                Row(modifier.horizontalScroll(rememberScrollState())) {
                    category.albums.forEach { album ->
                        AlbumCover(
                            album,
                            Modifier
                                .size(size)
                                .clickable { onAlbumClick(album.album) }
                        )
                    }

                    NewAlbumPlaceholder(
                        Modifier.size(size)
                            .clickable { onNewAlbumClick(category.category) }
                    )
                }
            }
        }
    }
}
