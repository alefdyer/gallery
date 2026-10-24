package com.asinosoft.photogallery.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.photogallery.data.Album
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.model.DateFilter
import com.asinosoft.photogallery.model.MediaType
import com.asinosoft.photogallery.model.ImageListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumView(
    onMediaClick: (Media, Set<String>, DateFilter?, MediaType?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    onSwitchAlbum: (album: Album, forward: Boolean) -> Unit = { _, _ -> },
    returnMediaId: Long? = null,
    onReturnHandled: () -> Unit = {},
    model: ImageListViewModel = hiltViewModel(),
) {
    // Соседи по категории запрашиваются сразу при открытии альбома (см. ImageListViewModel),
    // чтобы листать свайпом можно было без ожидания загрузки панелей.
    val siblings by model.siblingAlbums.collectAsState()
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
