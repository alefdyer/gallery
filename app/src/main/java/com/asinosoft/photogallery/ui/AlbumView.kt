package com.asinosoft.photogallery.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.photogallery.data.Album
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.model.DateFilter
import com.asinosoft.photogallery.model.MediaType
import com.asinosoft.photogallery.model.ImageListViewModel
import com.asinosoft.photogallery.ui.component.LocalAlbumHeavyContentReady
import kotlinx.coroutines.delay

/** Чуть дольше ALBUM_SLIDE_MS: список часто приходит уже после конца transition. */
private const val ALBUM_HEAVY_CONTENT_DELAY_MS = 400L

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

    // Таймер от первого кадра экрана, не от isOpeningTransition: медиа часто
    // приходит уже после slide-in, и тогда transition-флаг уже false.
    var heavyContentReady by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(ALBUM_HEAVY_CONTENT_DELAY_MS)
        heavyContentReady = true
    }

    CompositionLocalProvider(LocalAlbumHeavyContentReady provides heavyContentReady) {
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
                showFeatureTips = true,
                model = model
            )
        }
    }
}
