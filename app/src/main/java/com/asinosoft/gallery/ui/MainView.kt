package com.asinosoft.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.ImageListViewModel
import com.asinosoft.gallery.ui.component.FilterDialog
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(
    onMediaClick: (Media) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    model: ImageListViewModel = hiltViewModel()
) {
    val isFetching by model.isFetching.collectAsState(false)
    val pagerState = rememberPagerState { 2 }
    val coroutineScope = rememberCoroutineScope()
    val selection by model.selection.collectAsState()
    val topScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val showFilters = remember { mutableStateOf(false) }

    Scaffold(modifier) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isFetching,
            onRefresh = model::fetch,
            Modifier.padding(paddingValues)
        ) {
            Column {
                AnimatedVisibility(visible = selection.isEmpty()) {
                    TopAppBar(
                        title = { },
                        actions = {
                            if (selection.isEmpty() && 0 == pagerState.currentPage) {
                                IconButton(onClick = { showFilters.value = true}) {
                                    Icon(painterResource(R.drawable.filter), contentDescription = null)
                                }
                            }
                            IconButton(onClick = onSettingsClick ) {
                                Icon(painterResource(R.drawable.settings), contentDescription = null)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent
                        ),
                        scrollBehavior = topScroll
                    )
                }

                HorizontalPager(state = pagerState) {
                    when (it) {
                        0 -> ImageListView(
                            onMediaClick = onMediaClick,
                            onClose = {},
                            nestedScroll = topScroll.nestedScrollConnection
                        )

                        1 -> AlbumListView(
                            onAlbumClick = onAlbumClick,
                            nestedScroll = topScroll.nestedScrollConnection
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = selection.isEmpty(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ViewModeBar(
                    pagerState = pagerState,
                    onPhotos = { coroutineScope.launch { pagerState.scrollToPage(0) } },
                    onAlbums = { coroutineScope.launch { pagerState.scrollToPage(1) } },
                )
            }

            if (showFilters.value) {
                val filters by model.filters.collectAsState()
                FilterDialog(
                    filters = filters,
                    onChangeFilter = model::setFilter,
                    onDismiss = { showFilters.value = false }
                )
            }
        }
    }
}

@Composable
fun ViewModeBar(
    pagerState: PagerState,
    onPhotos: () -> Unit,
    onAlbums: () -> Unit,
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
        Spacer(Modifier.width(60.dp))
    }
}
