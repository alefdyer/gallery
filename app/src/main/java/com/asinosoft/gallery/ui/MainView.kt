package com.asinosoft.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.ImageListViewModel
import com.asinosoft.gallery.ui.theme.GalleryTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

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
    val filters by model.filters.collectAsState(listOf())
    val pagerState = rememberPagerState { 2 }
    val coroutineScope = rememberCoroutineScope()
    val selection by model.selection.collectAsState()
    val topScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()

    var navbarHeight by remember { mutableFloatStateOf(0f) }
    var navbarOffset by remember { mutableFloatStateOf(0f) }
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
            animate(initialValue = topScroll.state.heightOffset, targetValue = 0f) { v, _ ->
                topScroll.state.heightOffset = v
            }
        }
    }

    val navbarScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                lastScrollTime = System.currentTimeMillis()
                val delta = available.y
                val newOffset = navbarOffset - delta
                navbarOffset = newOffset.coerceIn(0f, navbarHeight)
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                lastScrollTime = System.currentTimeMillis()
                val targetOffset = if (navbarOffset > navbarHeight / 2f) navbarHeight else 0f
                coroutineScope.launch {
                    animate(
                        initialValue = navbarOffset,
                        targetValue = targetOffset,
                        initialVelocity = 10f
                    ) { y, _ ->
                        navbarOffset = y
                    }
                }

                return super.onPostFling(consumed, available)
            }
        }
    }

    val density = LocalDensity.current
    val topBarPadding = 8.dp

    Scaffold(
        modifier = modifier
            .nestedScroll(navbarScrollConnection)
            .nestedScroll(topScroll.nestedScrollConnection)
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
                HorizontalPager(state = pagerState) {
                    when (it) {
                        0 -> ImageListView(
                            onMediaClick = onMediaClick,
                            onClose = {},
                            scrollBehavior = topScroll,
                            contentPadding = contentPadding
                        )

                        1 -> AlbumListView(
                            onAlbumClick = onAlbumClick,
                            nestedScroll = topScroll.nestedScrollConnection,
                            contentPadding = contentPadding
                        )
                    }
                }

                AnimatedVisibility(
                    visible = selection.isEmpty(),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = topBarPadding + paddingValues.calculateTopPadding(), end = 8.dp)
                        .onGloballyPositioned {
                            topScroll.state.heightOffsetLimit =
                                -it.size.height.toFloat() - with(density) { (topBarPadding + paddingValues.calculateTopPadding()).toPx() }
                        }
                        .offset { IntOffset(0, topScroll.state.heightOffset.toInt()) }
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                        tonalElevation = 4.dp
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (0 == pagerState.currentPage && filters.isNotEmpty()) {
                                LazyRow(
                                    modifier = Modifier.widthIn(max = 240.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    items(filters, key = { it.application.pkg }) { filter ->
                                        filter.application.icon?.let { icon ->
                                            Image(
                                                bitmap = icon.toBitmap().asImageBitmap(),
                                                contentDescription = filter.application.name,
                                                modifier = Modifier
                                                    .padding(4.dp)
                                                    .size(32.dp)
                                                    .alpha(if (filter.enabled) 1f else 0.3f)
                                                    .clickable { model.toggleFilter(filter) }
                                            )
                                        }
                                    }
                                }
                            }

                            IconButton(onClick = onSettingsClick) {
                                Icon(
                                    painterResource(R.drawable.settings),
                                    contentDescription = null
                                )
                            }
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = selection.isEmpty(), modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                ViewModeBar(
                    pagerState = pagerState,
                    onPhotos = { coroutineScope.launch { pagerState.scrollToPage(0) } },
                    onAlbums = { coroutineScope.launch { pagerState.scrollToPage(1) } },
                    modifier = Modifier
                        .padding(bottom = paddingValues.calculateBottomPadding())
                        .onGloballyPositioned {
                            navbarHeight = it.size.height.toFloat() + with(density) { paddingValues.calculateBottomPadding().toPx() }
                        }
                        .offset { IntOffset(0, navbarOffset.toInt()) }
                )
            }
        }
    }
}

@Composable
private fun ViewModeBar(
    pagerState: PagerState,
    onPhotos: () -> Unit,
    onAlbums: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.padding(16.dp),
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface.copy(alpha = .8f),
    ) {
        val size = Modifier.width(96.dp)
        Row(Modifier.padding(2.dp)) {
            EllipseButton(
                onClick = onPhotos,
                selected = 0 == pagerState.currentPage,
                icon = painterResource(R.drawable.photo),
                label = stringResource(R.string.photos),
                modifier = size
            )

            EllipseButton(
                onClick = onAlbums,
                selected = 1 == pagerState.currentPage,
                icon = painterResource(R.drawable.album),
                label = stringResource(R.string.albums),
                modifier = size
            )
        }
    }
}

@Composable
private fun EllipseButton(
    onClick: () -> Unit,
    selected: Boolean,
    icon: Painter,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = .8f) else Color.Transparent
    ) {
        TextButton(
            onClick = onClick,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    painter = icon, contentDescription = label
                )
                Text(
                    text = label, style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Preview
@Composable
private fun Preview() {
    GalleryTheme {
        val pagerState = rememberPagerState { 2 }
        ViewModeBar(pagerState, {}, {})
    }
}
