package com.asinosoft.photogallery.ui

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
import androidx.compose.runtime.mutableStateOf
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
import com.asinosoft.photogallery.data.Album
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.model.DateFilter
import com.asinosoft.photogallery.model.MediaType
import com.asinosoft.photogallery.model.ImageListViewModel
import com.asinosoft.photogallery.ui.component.CachingProgressIndicator
import com.asinosoft.photogallery.ui.component.ViewModeBar
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainView(
    onMediaClick: (Media, Set<String>, DateFilter?, MediaType?) -> Unit,
    onAlbumClick: (Album) -> Unit,
    onStorageClick: (Storage) -> Unit,
    onSettingsClick: () -> Unit,
    onFavoritesClick: () -> Unit,
    onPrivateAlbumClick: () -> Unit,
    onTrashClick: () -> Unit,
    modifier: Modifier = Modifier,
    returnMediaId: Long? = null,
    onReturnHandled: () -> Unit = {},
    model: ImageListViewModel = hiltViewModel()
) {
    val isFetching by model.isFetching.collectAsState(false)
    val pagerState = rememberPagerState { 2 }
    val coroutineScope = rememberCoroutineScope()
    val selection by model.selection.collectAsState()

    var navbarHeight by remember { mutableFloatStateOf(0f) }
    var navbarOffset by remember { mutableFloatStateOf(0f) }
    var lastScrollTime by remember { mutableLongStateOf(0L) }
    // См. ImageListView: snap и возврат не должны анимировать смещение одновременно.
    var navbarAnimJob by remember { mutableStateOf<Job?>(null) }
    var navbarReturning by remember { mutableStateOf(false) }

    LaunchedEffect(lastScrollTime) {
        if (lastScrollTime == 0L) return@LaunchedEffect
        delay(500.milliseconds)
        navbarAnimJob?.cancel()
        navbarReturning = true
        navbarAnimJob = launch {
            animate(initialValue = navbarOffset, targetValue = 0f) { v, _ ->
                navbarOffset = v
            }
        }
        navbarAnimJob?.invokeOnCompletion { navbarReturning = false }
    }

    val syncPanelsScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                navbarReturning = false
                val newNavbarOffset = navbarOffset + abs(available.y)
                navbarOffset = newNavbarOffset.coerceIn(0f, navbarHeight)

                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                // Таймер — только по реальному движению: в onPreScroll у краёв списка приходят
                // «сырые» дельты overscroll уже после остановки, и панели выезжали с опозданием.
                if (consumed.y != 0f) {
                    lastScrollTime = System.currentTimeMillis()
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (consumed.y != 0f) {
                    lastScrollTime = System.currentTimeMillis()
                }
                // Поздний post-fling у края не должен отменять уже идущий возврат панели.
                if (!navbarReturning) {
                    val targetNavbarOffset =
                        if (navbarOffset > navbarHeight / 2f) navbarHeight else 0f

                    navbarAnimJob?.cancel()
                    navbarAnimJob = coroutineScope.launch {
                        animate(
                            initialValue = navbarOffset,
                            targetValue = targetNavbarOffset,
                            initialVelocity = 10f
                        ) { y, _ ->
                            navbarOffset = y
                        }
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

                HorizontalPager(state = pagerState, userScrollEnabled = false) { page ->
                    key(page) {
                        when (page) {
                            0 -> ImageListView(
                                onMediaClick = onMediaClick,
                                onClose = {},
                                scrollBehavior = null,
                                contentPadding = contentPadding,
                                bottomPanelHeight = with(density) { navbarHeight.toDp() } - 24.dp,
                                returnMediaId = returnMediaId,
                                onReturnHandled = onReturnHandled,
                                showFeatureTips = true
                            )

                            1 -> AlbumListView(
                                onAlbumClick = onAlbumClick,
                                onStorageClick = onStorageClick,
                                nestedScroll = syncPanelsScrollConnection,
                                contentPadding = contentPadding,
                                bottomPanelHidden = navbarHeight > 0f && navbarOffset > navbarHeight / 2f
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
                onFavorites = onFavoritesClick,
                onPrivateAlbum = onPrivateAlbumClick,
                onTrash = onTrashClick,
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
