package com.asinosoft.gallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Photo
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshContainer
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.Image
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MainView(
    images: List<Image>,
    albums: List<Album>,
    onImageClick: (Image) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onRescan: () -> Unit,
) {
    val pagerState = rememberPagerState { 2 }
    val coroutineScope = rememberCoroutineScope()
    val pullState = rememberPullToRefreshState()
    if (pullState.isRefreshing) {
        LaunchedEffect(true) {
            delay(1000)
            onRescan()
            pullState.endRefresh()
        }
    }

    Box {
        Scaffold(
            modifier = Modifier.nestedScroll(pullState.nestedScrollConnection),
            bottomBar = {
                ViewModeBar(
                    pagerState = pagerState,
                    onPhotos = { coroutineScope.launch { pagerState.scrollToPage(0) } },
                    onAlbums = { coroutineScope.launch { pagerState.scrollToPage(1) } }
                )
            },
        ) {
            HorizontalPager(
                modifier = Modifier.padding(it),
                state = pagerState
            ) {
                when (it) {
                    0 -> ImageListView(images = images, onImageClick = onImageClick)
                    1 -> AlbumListView(albums = albums, onAlbumClick = onAlbumClick)
                }
            }


        }

        PullToRefreshContainer(
            modifier = Modifier.align(Alignment.TopCenter),
            state = pullState
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ViewModeBar(
    modifier: Modifier = Modifier,
    pagerState: PagerState,
    onPhotos: () -> Unit,
    onAlbums: () -> Unit,
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
                    imageVector = Icons.Default.Photo,
                    contentDescription = stringResource(id = R.string.photos)
                )
            },
            label = { Text(stringResource(id = R.string.photos)) },
        )
        NavigationBarItem(
            selected = 1 == pagerState.currentPage,
            onClick = onAlbums,
            icon = {
                Icon(
                    imageVector = Icons.Default.PhotoLibrary,
                    contentDescription = stringResource(id = R.string.albums)
                )
            },
            label = { Text(stringResource(id = R.string.albums)) },
        )
        Spacer(Modifier.width(60.dp))
    }
}
