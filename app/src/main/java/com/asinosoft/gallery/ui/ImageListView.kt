package com.asinosoft.gallery.ui

import android.icu.text.DateFormatSymbols
import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animate
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.storage.StorageType
import com.asinosoft.gallery.model.DateFilter
import com.asinosoft.gallery.model.ImageListViewModel
import com.asinosoft.gallery.ui.component.AddToAlbumDialog
import com.asinosoft.gallery.ui.component.DragSelectionState
import com.asinosoft.gallery.ui.component.FilterBar
import com.asinosoft.gallery.ui.component.ImageListHeaderBackground
import com.asinosoft.gallery.ui.component.ImageListHeaderInfo
import com.asinosoft.gallery.ui.component.LazyGridVerticalScrollIndicator
import com.asinosoft.gallery.ui.component.MediaThumbnail
import com.asinosoft.gallery.ui.component.SelectionControlBar
import com.asinosoft.gallery.ui.component.SelectionInfoBar
import com.asinosoft.gallery.ui.component.ShadowedHeader
import com.asinosoft.gallery.ui.component.dragSelection
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageListView(
    onMediaClick: (Media, Set<String>, DateFilter?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    bottomPanelHeight: Dp = 0.dp,
    model: ImageListViewModel = hiltViewModel()
) {
    val images by model.images.collectAsState(listOf())
    val filters by model.filters.collectAsState(listOf())
    val selection by model.selection.collectAsState()

    var closeOnEmptyList by remember { mutableStateOf(false) }
    val lazyGridState = rememberLazyGridState()
    var showTagDialog by remember { mutableStateOf(false) }
    val dragSelectionState = remember { DragSelectionState() }
    val date by remember(images, lazyGridState) {
        derivedStateOf {
            images.getOrNull(lazyGridState.firstVisibleItemIndex)?.date?.let {
                "${months[it.monthValue - 1]} ${it.year}"
            }
        }
    }

    val headerVisible by remember(images, lazyGridState) {
        derivedStateOf {
            images.isNotEmpty() && lazyGridState.layoutInfo.visibleItemsInfo.firstOrNull()?.index == 0
        }
    }

    var previewIds by remember { mutableStateOf(emptySet<Long>()) }
    LaunchedEffect(images, lazyGridState) {
        val appearedAt = mutableMapOf<Long, Long>()
        snapshotFlow {
            val visibleVideos = lazyGridState.layoutInfo.visibleItemsInfo
                .mapNotNull { images.getOrNull(it.index) }
                .filter { null != it.video && it.storageType == StorageType.LOCAL }
                .map { it.id }
            visibleVideos to lazyGridState.isScrollInProgress
        }.collectLatest { (visibleVideos, isScrolling) ->
            val now = SystemClock.uptimeMillis()
            appearedAt.keys.retainAll(visibleVideos.toSet())
            visibleVideos.forEach { appearedAt.getOrPut(it) { now } }

            if (isScrolling) {
                previewIds = previewIds intersect visibleVideos.toSet()
                return@collectLatest
            }

            val candidates = visibleVideos.take(MAX_VIDEO_PREVIEWS)
            previewIds = previewIds intersect candidates.toSet()
            candidates
                .filterNot { it in previewIds }
                .sortedBy { appearedAt.getValue(it) }
                .forEach { id ->
                    val wait = appearedAt.getValue(id) + VIDEO_PREVIEW_DELAY_MS - SystemClock.uptimeMillis()
                    if (wait > 0) delay(wait)
                    previewIds = previewIds + id
                }
        }
    }

    LaunchedEffect(model.albumId, images, onClose) {
        if (closeOnEmptyList && images.isEmpty()) {
            onClose()
        } else {
            closeOnEmptyList = null != model.albumId
        }
    }

    BackHandler(selection.isNotEmpty(), model::clearSelection)

    BackHandler(filters.any { !it.enabled }, model::clearFilters)

    var filtersHeight by remember { mutableFloatStateOf(0f) }
    var filtersOffset by remember { mutableFloatStateOf(0f) }
    var lastScrollTime by remember { mutableLongStateOf(0L) }

    LaunchedEffect(lastScrollTime) {
        if (lastScrollTime == 0L) return@LaunchedEffect
        delay(500.milliseconds)
        launch {
            animate(initialValue = filtersOffset, targetValue = 0f) { v, _ ->
                filtersOffset = v
            }
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val nestedScrollConnection = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                filtersOffset = (filtersOffset - available.y).coerceIn(0f, filtersHeight)

                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                lastScrollTime = System.currentTimeMillis()
                return available
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                lastScrollTime = System.currentTimeMillis()
                val targetFiltersOffset = if (filtersOffset > filtersHeight / 2f) filtersHeight else 0f

                coroutineScope.launch {
                    animate(
                        initialValue = filtersOffset,
                        targetValue = targetFiltersOffset,
                        initialVelocity = 10f
                    ) { y, _ ->
                        filtersOffset = y
                    }
                }

                return super.onPostFling(consumed, available)
            }
        }
    }

    val album by model.album.collectAsState()
    val title = album?.name ?: stringResource(R.string.all_photos)
    val cover = images.firstOrNull()
    val photoCount by model.photoCount.collectAsState()
    val videoCount by model.videoCount.collectAsState()
    val showPhotos by model.showPhotos.collectAsState()
    val showVideos by model.showVideos.collectAsState()
    val canToggleMediaType = photoCount > 0 && videoCount > 0
    val layoutDirection = LocalLayoutDirection.current

    BoxWithConstraints(modifier.fillMaxSize()) {
        val gap = 2.dp
        val rowHeight = (maxWidth - gap * 2) / 3
        val headerHeight = contentPadding.calculateTopPadding() + rowHeight
        val gridPadding = if (null == cover) contentPadding else PaddingValues(
            start = contentPadding.calculateStartPadding(layoutDirection),
            top = headerHeight + gap,
            end = contentPadding.calculateEndPadding(layoutDirection),
            bottom = contentPadding.calculateBottomPadding()
        )

        val headerModifier = Modifier
            .fillMaxWidth()
            .height(headerHeight)
            .graphicsLayer {
                val info = lazyGridState.layoutInfo
                val first = info.visibleItemsInfo.firstOrNull()
                translationY = if (first?.index == 0) {
                    (first.offset.y - info.viewportStartOffset) - size.height - gap.toPx()
                } else {
                    -size.height - gap.toPx()
                }
            }

        cover?.let { ImageListHeaderBackground(cover = it, modifier = headerModifier) }

        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Fixed(3),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = gridPadding,
            modifier = Modifier
                .nestedScroll(nestedScrollConnection)
                .dragSelection(
                    items = images,
                    state = lazyGridState,
                    currentSelection = { selection },
                    dragSelectionState = dragSelectionState,
                    onSelectedChange = model::setSelection,
                    contentPadding = gridPadding
                )
                .let {
                    if (scrollBehavior == null) it else it.nestedScroll(scrollBehavior.nestedScrollConnection)
                }
        ) {
            items(images, key = { it.id }) { media ->
                MediaThumbnail(
                    media = media,
                    selectionMode = selection.isNotEmpty(),
                    selected = selection,
                    playPreview = media.id in previewIds,
                    onClick = { media -> onMediaClick(media, model.activeFilterPackages.value, model.activeDateFilter.value) },
                    onSelect = { image ->
                        if (!dragSelectionState.active) {
                            model.toggleSelection(image)
                        }
                    }
                )
            }
        }

        if (null != cover) {
            ImageListHeaderInfo(
                title = title,
                photoCount = photoCount,
                videoCount = videoCount,
                photosEnabled = showPhotos,
                videosEnabled = showVideos,
                onPhotosClick = model::togglePhotos.takeIf { canToggleMediaType },
                onVideosClick = model::toggleVideos.takeIf { canToggleMediaType },
                modifier = headerModifier
            )
        }

        if (!headerVisible) date?.let { ShadowedHeader(it) }

        LazyGridVerticalScrollIndicator(
            lazyGridState = lazyGridState,
            listItems = images,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding() +
                        TOP_PANEL_MARGIN + TOP_PANEL_HEIGHT + SCROLL_INDICATOR_GAP,
                    bottom = maxOf(contentPadding.calculateBottomPadding(), bottomPanelHeight) +
                        SCROLL_INDICATOR_GAP,
                    end = 4.dp
                )
        )

        AnimatedVisibility(
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .offset {
                    IntOffset(
                        0,
                        scrollBehavior?.state?.heightOffset?.toInt() ?: 0
                    )
                },
            visible = selection.isNotEmpty()
        ) {
            SelectionInfoBar(
                selection = selection,
                onCancel = model::clearSelection
            )
        }

        AnimatedVisibility(
            enter = slideInVertically { it * 2 },
            exit = slideOutVertically { it * 2 },
            modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
            visible = selection.isNotEmpty()
        ) {
            SelectionControlBar(
                onShare = { model.shareSelection() },
                onDelete = { model.deleteSelection() },
                onAddTag = { showTagDialog = true },
                onRemoveTag = model.albumId?.let { { model.removeSelectionFromAlbum(it) } }
            )
        }

        val density = LocalDensity.current
        val statusBarHeight = WindowInsets.statusBars.getTop(density)
        FilterBar(
            visible = selection.isEmpty(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .statusBarsPadding()
                .padding(top = 8.dp, end = 8.dp)
                .offset { IntOffset(0, -filtersOffset.toInt()) }
                .onGloballyPositioned {
                    filtersHeight = it.size.height + statusBarHeight + 8 * density.density
                }
        )

        if (showTagDialog) {
            AddToAlbumDialog(
                onPickAlbum = { album ->
                    model.addSelectionToAlbum(album.id)
                    showTagDialog = false
                },
                onCreateAlbum = { name, category ->
                    model.addSelectionToNewAlbum(name, category)
                    showTagDialog = false
                },
                onDismiss = { showTagDialog = false }
            )
        }
    }
}

private const val MAX_VIDEO_PREVIEWS = 4
private val TOP_PANEL_MARGIN = 8.dp
private val TOP_PANEL_HEIGHT = 48.dp
private val SCROLL_INDICATOR_GAP = 8.dp
private const val VIDEO_PREVIEW_DELAY_MS = 2000L

private val months = DateFormatSymbols
    .getInstance()
    .getMonths(DateFormatSymbols.STANDALONE, DateFormatSymbols.WIDE)
