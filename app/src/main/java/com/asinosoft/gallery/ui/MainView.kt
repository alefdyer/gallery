package com.asinosoft.gallery.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.ImageListViewModel
import com.asinosoft.gallery.ui.component.CachingProgressIndicator
import com.asinosoft.gallery.ui.component.DateFolderTreeView
import com.asinosoft.gallery.ui.component.FilterBar
import com.asinosoft.gallery.ui.component.ViewModeBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(
    onMediaClick: (Media, Set<String>) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    model: ImageListViewModel = hiltViewModel()
) {
    val isFetching by model.isFetching.collectAsState(false)
    val filters by model.filters.collectAsState(listOf())
    val pagerState = rememberPagerState { 2 }
    val coroutineScope = rememberCoroutineScope()
    val selection by model.selection.collectAsState()
    val isFolderExplorerOpen by model.isFolderExplorerOpen.collectAsState()
    val activeDateFilter by model.activeDateFilter.collectAsState()

    var navbarHeight by remember { mutableFloatStateOf(0f) }
    var navbarOffset by remember { mutableFloatStateOf(0f) }
    var topbarHeight by remember { mutableFloatStateOf(0f) }
    var topbarOffset by remember { mutableFloatStateOf(0f) }
    var lastScrollTime by remember { mutableStateOf(0L) }

    LaunchedEffect(lastScrollTime) {
        if (lastScrollTime == 0L) return@LaunchedEffect
        delay(500.milliseconds)
        launch {
            animate(initialValue = navbarOffset, targetValue = 0f) { v, _ ->
                navbarOffset = v
            }
        }
        launch {
            animate(initialValue = topbarOffset, targetValue = 0f) { v, _ ->
                topbarOffset = v
            }
        }
    }

    val syncPanelsScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                lastScrollTime = System.currentTimeMillis()
                val delta = available.y

                val newNavbarOffset = navbarOffset - delta
                navbarOffset = newNavbarOffset.coerceIn(0f, navbarHeight)

                val newTopbarOffset = topbarOffset - delta
                topbarOffset = newTopbarOffset.coerceIn(0f, topbarHeight)

                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                lastScrollTime = System.currentTimeMillis()
                val targetNavbarOffset = if (navbarOffset > navbarHeight / 2f) navbarHeight else 0f
                val targetTopbarOffset = if (topbarOffset > topbarHeight / 2f) topbarHeight else 0f

                coroutineScope.launch {
                    animate(
                        initialValue = navbarOffset,
                        targetValue = targetNavbarOffset,
                        initialVelocity = 10f
                    ) { y, _ ->
                        navbarOffset = y
                    }
                }
                coroutineScope.launch {
                    animate(
                        initialValue = topbarOffset,
                        targetValue = targetTopbarOffset,
                        initialVelocity = 10f
                    ) { y, _ ->
                        topbarOffset = y
                    }
                }

                return super.onPostFling(consumed, available)
            }
        }
    }

    val density = LocalDensity.current
    val topBarPadding = 8.dp

    Scaffold(
        modifier = modifier.nestedScroll(syncPanelsScrollConnection)
    ) { paddingValues ->
        PullToRefreshBox(
            isRefreshing = isFetching,
            onRefresh = model::fetch,
            modifier = Modifier.fillMaxSize()
        ) {
            Box(Modifier.fillMaxSize()) {
                val contentPadding = PaddingValues(
                    top = 36.dp + paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding()
                )

                if (isFolderExplorerOpen) {
                    val images by model.images.collectAsState()
                    DateFolderTreeView(
                        images = images,
                        expandedNodes = model.expandedFolderNodes,
                        initialScrollIndex = model.treeListIndex,
                        initialScrollOffset = model.treeListOffset,
                        onUpdateScrollPosition = { idx, off ->
                            model.treeListIndex = idx
                            model.treeListOffset = off
                        },
                        onSelectDateFilter = model::setDateFilter,
                        contentPadding = contentPadding
                    )
                } else {
                    HorizontalPager(state = pagerState) { page ->
                        key(page) {
                            when (page) {
                                0 -> ImageListView(
                                    onMediaClick = onMediaClick,
                                    onClose = {},
                                    scrollBehavior = null,
                                    contentPadding = contentPadding
                                )

                                1 -> AlbumListView(
                                    onAlbumClick = onAlbumClick,
                                    nestedScroll = syncPanelsScrollConnection,
                                    contentPadding = contentPadding
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            top = topBarPadding + 8.dp + paddingValues.calculateTopPadding(),
                            start = 8.dp
                        )
                        .offset { IntOffset(0, -topbarOffset.toInt()) }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .clickable { model.toggleFolderExplorer() }
                            .padding(8.dp)
                            .size(32.dp)
                    ) {
                        val showBack = isFolderExplorerOpen || activeDateFilter != null
                        Icon(
                            painter = painterResource(
                                if (showBack) R.drawable.arrow_back else R.drawable.folder_yellow
                            ),
                            contentDescription = if (showBack) "Назад в список" else "Папка",
                            tint = if (showBack) MaterialTheme.colorScheme.onSurface else androidx.compose.ui.graphics.Color.Unspecified
                        )
                    }
                }

                FilterBar(
                    visible = selection.isEmpty() && pagerState.currentPage == 0 && !isFolderExplorerOpen,
                    filters = filters,
                    onToggleFilter = model::toggleFilter,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            top = topBarPadding + 8.dp + paddingValues.calculateTopPadding(),
                            end = 8.dp
                        )
                        .offset { IntOffset(0, -topbarOffset.toInt()) },
                    onMeasuredHeight = {
                        topbarHeight =
                            it + with(density) { (topBarPadding + 8.dp + paddingValues.calculateTopPadding()).toPx() }
                    }
                )
            }

            CachingProgressIndicator(paddingValues)

            ViewModeBar(
                visible = selection.isEmpty(),
                pagerState = pagerState,
                onPhotos = { coroutineScope.launch { pagerState.scrollToPage(0) } },
                onAlbums = { coroutineScope.launch { pagerState.scrollToPage(1) } },
                onSettings = onSettingsClick,
                modifier = Modifier
                    .padding(bottom = paddingValues.calculateBottomPadding())
                    .offset(y = 8.dp)
                    .onGloballyPositioned {
                        navbarHeight = it.size.height.toFloat() + with(density) {
                            paddingValues.calculateBottomPadding().toPx()
                        }
                    }
                    .offset { IntOffset(0, navbarOffset.toInt()) }
            )
        }
    }
}
