package com.asinosoft.gallery.ui

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.data.storage.StorageCheckResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(
    images: List<Media>,
    albums: List<Album>,
    storages: List<Storage>,
    onMediaClick: (Media) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onShare: (Set<Long>) -> Unit,
    onDelete: (Set<Long>, () -> Unit) -> Unit,
    onAddTag: (Set<Long>, Long) -> Unit,
    onCreateTag: (Set<Long>, String) -> Unit,
    onRemoveTag: (Set<Long>, Long) -> Unit,
    onCheckStorage: suspend (Storage) -> StorageCheckResult,
    onAddStorage: (Storage) -> Unit,
    onDeleteStorage: (Storage) -> Unit,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier
) {
    val pagerState = rememberPagerState { 3 }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        modifier,
        bottomBar = {
            ViewModeBar(
                pagerState = pagerState,
                onPhotos = { coroutineScope.launch { pagerState.scrollToPage(0) } },
                onAlbums = { coroutineScope.launch { pagerState.scrollToPage(1) } },
                onStorages = { coroutineScope.launch { pagerState.scrollToPage(2) } }
            )
        }
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh,
            Modifier.padding(paddingValues)
        ) {
            HorizontalPager(state = pagerState) {
                when (it) {
                    0 -> ImageListView(
                        albums = albums,
                        media = images,
                        onClick = onMediaClick,
                        onClose = {},
                        onShare = onShare,
                        onDelete = onDelete,
                        onAddTag = onAddTag,
                        onCreateTag = onCreateTag,
                        onRemoveTag = onRemoveTag
                    )

                    1 -> AlbumListView(
                        albums = albums,
                        onAlbumClick = onAlbumClick
                    )

                    2 -> StoragesView(
                        storages = storages,
                        onCheckStorageConnection = onCheckStorage,
                        onAddStorage = onAddStorage,
                        onDeleteStorage = onDeleteStorage
                    )
                }
            }
        }
    }
}

@Composable
fun ViewModeBar(
    pagerState: PagerState,
    onPhotos: () -> Unit,
    onAlbums: () -> Unit,
    onStorages: () -> Unit,
    modifier: Modifier = Modifier
) {
    NavigationBar(
        modifier = modifier,
        containerColor = Color.White.copy(0.8f)
    ) {
        Spacer(Modifier.width(60.dp))
        NavigationBarItem(
            selected = 0 == pagerState.currentPage,
            onClick = onPhotos,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.photo),
                    contentDescription = stringResource(id = R.string.photos)
                )
            },
            label = { Text(stringResource(id = R.string.photos)) }
        )
        NavigationBarItem(
            selected = 1 == pagerState.currentPage,
            onClick = onAlbums,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.album),
                    contentDescription = stringResource(id = R.string.albums)
                )
            },
            label = { Text(stringResource(id = R.string.albums)) }
        )
        NavigationBarItem(
            selected = 2 == pagerState.currentPage,
            onClick = onStorages,
            icon = {
                Icon(
                    painter = painterResource(R.drawable.album),
                    contentDescription = stringResource(id = R.string.storages)
                )
            },
            label = { Text(stringResource(id = R.string.storages)) }
        )
        Spacer(Modifier.width(60.dp))
    }
}
