package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.model.AlbumsViewModel

@Composable
fun AlbumSelector(
    modifier: Modifier = Modifier,
    onAlbumClick: (Album) -> Unit,
    onNewAlbumClick: () -> Unit,
    model: AlbumsViewModel = hiltViewModel()
) {
    val categories by model.albums.collectAsState(initial = listOf())

    LazyColumn(
        modifier = modifier.fillMaxSize()
    ) {
        items(categories, { it.category.id }) { category ->
            Column(modifier) {
                val categoryName = if (":other" == category.category.name)
                    stringResource(R.string.other)
                else
                    category.category.name

                Text(categoryName, style = MaterialTheme.typography.headlineMedium)

                LazyRow {
                    items(category.albums, { it.album.id }) { album ->
                        AlbumCover(
                            album,
                            Modifier
                                .size(LocalWindowInfo.current.containerDpSize.width / 2)
                                .clickable { onAlbumClick(album.album) }
                        )
                    }
                }
            }
        }

        item {
            NewAlbumPlaceholder(
                Modifier.size(LocalWindowInfo.current.containerDpSize.width / 2)
                    .clickable { onNewAlbumClick() }
            )
        }
    }
}
