package com.asinosoft.gallery.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.AlbumsViewModel
import com.asinosoft.gallery.model.DateFilter
import com.asinosoft.gallery.model.MediaType
import com.asinosoft.gallery.model.ImageListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumView(
    onMediaClick: (Media, Set<String>, DateFilter?, MediaType?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onSwitchAlbum: (album: Album, forward: Boolean) -> Unit = { _, _ -> },
    loadNeighbours: Boolean = true,
    returnMediaId: Long? = null,
    onReturnHandled: () -> Unit = {},
    model: ImageListViewModel = hiltViewModel(),
) {
    var neighboursRequested by remember { mutableStateOf(loadNeighbours) }
    LaunchedEffect(loadNeighbours) {
        if (loadNeighbours) neighboursRequested = true
    }

    val siblings = if (neighboursRequested) {
        val albumsModel: AlbumsViewModel = hiltViewModel()
        val categories by albumsModel.albums.collectAsState()
        remember(categories, model.albumId) {
            categories
                .firstOrNull { category -> category.albums.any { it.album.id == model.albumId } }
                ?.albums
                ?.map { it.album }
                .orEmpty()
        }
    } else {
        emptyList()
    }
    val index = siblings.indexOfFirst { it.id == model.albumId }

    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { paddingValues ->
        ImageListView(
            onMediaClick,
            onClose,
            contentPadding = PaddingValues(
                top = 36.dp + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding()
            ),
            onBack = onClose,
            returnMediaId = returnMediaId,
            onReturnHandled = onReturnHandled,
            previousAlbum = siblings.getOrNull(index - 1).takeIf { index >= 0 },
            nextAlbum = siblings.getOrNull(index + 1).takeIf { index >= 0 },
            onSwitchAlbum = onSwitchAlbum,
            model = model
        )
    }
}
