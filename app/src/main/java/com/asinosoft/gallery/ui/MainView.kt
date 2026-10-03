package com.asinosoft.gallery.ui

import androidx.compose.animation.core.animate
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.DateFilter
import com.asinosoft.gallery.model.ImageListViewModel
import com.asinosoft.gallery.ui.component.CachingProgressIndicator
import com.asinosoft.gallery.ui.component.ViewModeBar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(
    onMediaClick: (Media, Set<String>, DateFilter?) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onSettingsClick: () -> Unit,
    modifier: Modifier = Modifier,
    model: ImageListViewModel = hiltViewModel()
) {
    val isFetching by model.isFetching.collectAsState(false)
    val pagerState = rememberPagerState { 2 }
    val coroutineScope = rememberCoroutineScope()
    val selection by model.selection.collectAsState()

    var navbarHeight by remember { mutableFloatStateOf(0f) }
    var navbarOffset by remember { mutableFloatStateOf(0f) }
    var lastScrollTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(lastScrollTime) {
        if (lastScrollTime == 0L) return@LaunchedEffect
        delay(500.milliseconds)
        launch {
            animate(initialValue = navbarOffset, targetValue = 0f) { v, _ ->
                navbarOffset = v
            }
        }
    }

    val syncPanelsScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                lastScrollTime = System.currentTimeMillis()
                val newNavbarOffset = navbarOffset - available.y
                navbarOffset = newNavbarOffset.coerceIn(0f, navbarHeight)

                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                lastScrollTime = System.currentTimeMillis()
                val targetNavbarOffset = if (navbarOffset > navbarHeight / 2f) navbarHeight else 0f

                coroutineScope.launch {
                    animate(
                        initialValue = navbarOffset,
                        targetValue = targetNavbarOffset,
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
