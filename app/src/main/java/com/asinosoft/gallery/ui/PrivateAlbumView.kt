package com.asinosoft.gallery.ui

import android.app.Activity
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemGestures
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.MediaFolderViewModel
import com.asinosoft.gallery.model.MediaViewModel
import com.asinosoft.gallery.model.PrivateAlbumViewModel
import com.asinosoft.gallery.ui.component.DateFilterDialog
import com.asinosoft.gallery.ui.component.DismissDragState
import com.asinosoft.gallery.ui.component.DragSelectionState
import com.asinosoft.gallery.ui.component.ImageListHeaderBackground
import com.asinosoft.gallery.ui.component.ImageListHeaderInfo
import com.asinosoft.gallery.ui.component.LabeledIconButton
import com.asinosoft.gallery.ui.component.LocalDismissDrag
import com.asinosoft.gallery.ui.component.LocalSharedElementVisible
import com.asinosoft.gallery.ui.component.MediaSharedTransitionLayout
import com.asinosoft.gallery.ui.component.MediaThumbnail
import com.asinosoft.gallery.ui.component.SHARED_ELEMENT_MS
import com.asinosoft.gallery.ui.component.SelectionInfoBar
import com.asinosoft.gallery.ui.component.dragSelection
import com.asinosoft.gallery.ui.component.dragToDismiss
import com.asinosoft.gallery.ui.component.rememberVideoPreviewQueue
import com.asinosoft.gallery.ui.theme.floatingPanelColor

private val HEADER_TOP = 36.dp
private const val GRID_COLUMNS = 3

@Composable
fun PrivateAlbumView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    model: PrivateAlbumViewModel = hiltViewModel()
) {
    SecureWindow()
    CloseWhenHidden(onClose)

    MediaFolderView(
        title = stringResource(R.string.private_album),
        emptyText = stringResource(R.string.private_album_empty),
        restoreLabel = stringResource(R.string.restore),
        onClose = onClose,
        model = model,
        modifier = modifier
    )
}

/** Сетка файлов вне основной галереи с фильтрами, выделением и кнопками «вернуть» / «удалить» */
@Composable
internal fun MediaFolderView(
    title: String,
    emptyText: String,
    restoreLabel: String,
    onClose: () -> Unit,
    model: MediaFolderViewModel,
    modifier: Modifier = Modifier,
) {
    MediaSharedTransitionLayout(modifier.fillMaxSize()) {
        MediaFolderContent(title, emptyText, restoreLabel, onClose, model)
    }
}

@Composable
private fun MediaFolderContent(
    title: String,
    emptyText: String,
    restoreLabel: String,
    onClose: () -> Unit,
    model: MediaFolderViewModel,
    modifier: Modifier = Modifier,
) {
    val media by model.media.collectAsState()
    val items by model.visibleMedia.collectAsState()
    val selection by model.selection.collectAsState()
    val dateFilter by model.dateFilter.collectAsState()
    val showPhotos by model.showPhotos.collectAsState()
    val showVideos by model.showVideos.collectAsState()
    val photoCount by model.photoCount.collectAsState()
    val videoCount by model.videoCount.collectAsState()
    var pagerOpen by rememberSaveable { mutableStateOf(false) }
    var pagerIndex by rememberSaveable { mutableIntStateOf(0) }
    var pagerMediaId by rememberSaveable { mutableStateOf<Long?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var showDateDialog by remember { mutableStateOf(false) }
    val thumbnailModel: MediaViewModel = hiltViewModel()
    val lazyGridState = rememberLazyGridState()
    val dragSelectionState = remember { DragSelectionState() }
    val canToggleMediaType = photoCount > 0 && videoCount > 0

    BackHandler(selection.isNotEmpty(), model::clearSelection)
    BackHandler(null != dateFilter) { model.setDateFilter(null) }

    LaunchedEffect(dateFilter, showPhotos, showVideos) {
        lazyGridState.scrollToItem(0)
    }
    LaunchedEffect(media, dateFilter, photoCount + videoCount) {
        if (null != dateFilter && !media.isNullOrEmpty() && 0 == photoCount + videoCount) {
            model.setDateFilter(null)
        }
    }

    if (showDateDialog) {
        val yearGroups by model.dateGroups.collectAsState()
        DateFilterDialog(
            onClose = { showDateDialog = false },
            yearGroups = yearGroups,
            selectedDate = dateFilter,
            onSelectDateFilter = {
                showDateDialog = false
                model.setDateFilter(it)
            },
            onClearDateFilter = {
                showDateDialog = false
                model.setDateFilter(null)
            }
        )
    }

    val closePager = { page: Int ->
        if (lazyGridState.layoutInfo.visibleItemsInfo.none { it.index == page }) {
            lazyGridState.requestScrollToItem((page - GRID_COLUMNS).coerceAtLeast(0))
        }
        pagerMediaId = items.getOrNull(page)?.id
        pagerOpen = false
    }

    BoxWithConstraints(modifier
        .fillMaxSize()
        .background(MaterialTheme.colorScheme.background)) {
        val videoPreview = rememberVideoPreviewQueue(items, lazyGridState)
        val cover = items.firstOrNull()

        val gap = 2.dp
        val topPadding = HEADER_TOP + WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val bottomPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
        val headerHeight = topPadding + (maxWidth - gap * 2) / 3
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

        val gridPadding = PaddingValues(top = headerHeight + gap, bottom = bottomPadding)
        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Fixed(3),
            contentPadding = gridPadding,
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalArrangement = Arrangement.spacedBy(gap),
            modifier = Modifier
                .fillMaxSize()
                .dragSelection(
                    items = items,
                    state = lazyGridState,
                    currentSelection = { selection },
                    dragSelectionState = dragSelectionState,
                    onSelectedChange = model::setSelection,
                    contentPadding = gridPadding
                )
        ) {
            itemsIndexed(items, key = { _, item -> item.id }) { index, item ->
                CompositionLocalProvider(LocalSharedElementVisible provides !(pagerOpen && item.id == pagerMediaId)) {
                    MediaThumbnail(
                        media = item,
                        model = thumbnailModel,
                        persistentCache = false,
                        selectionMode = selection.isNotEmpty(),
                        selected = selection,
                        playPreview = !pagerOpen && item.id == videoPreview.previewId,
                        sharedElement = true,
                        onPreviewEnded = videoPreview::onEnded,
                        onClick = {
                            pagerIndex = index
                            pagerMediaId = item.id
                            pagerOpen = true
                        },
                        onSelect = { if (!dragSelectionState.active) model.toggleSelection(it) }
                    )
                }
            }
        }

        if (null != cover) {
            ImageListHeaderInfo(
                title = dateFilter?.let { dateFilterTitle(it) } ?: title,
                photoCount = photoCount,
                videoCount = videoCount,
                photosEnabled = !canToggleMediaType || showPhotos,
                videosEnabled = !canToggleMediaType || showVideos,
                onPhotosClick = model::togglePhotos.takeIf { canToggleMediaType },
                onVideosClick = model::toggleVideos.takeIf { canToggleMediaType },
                modifier = headerModifier
            )
        }

        if (true == media?.isEmpty()) {
            Text(
                text = emptyText,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
            )
        }

        if (selection.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(50),
                color = floatingPanelColor(),
                tonalElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(start = 8.dp, top = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        contentAlignment = Alignment.CenterEnd,
                        modifier = Modifier
                            .size(width = 40.dp, height = 48.dp)
                            .clickable(onClick = onClose)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.back),
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    if (true == media?.isNotEmpty()) {
                        IconButton(onClick = { showDateDialog = true }) {
                            Icon(
                                painter = painterResource(
                                    if (null == dateFilter) R.drawable.calendar_color
                                    else R.drawable.calendar_color_selected
                                ),
                                contentDescription = null,
                                tint = Color.Unspecified,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    }
                    if (null != dateFilter) {
                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .size(width = 36.dp, height = 48.dp)
                                .clickable { model.setDateFilter(null) }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = selection.isNotEmpty(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
        ) {
            SelectionInfoBar(selection = selection, onCancel = model::clearSelection)
        }

        AnimatedVisibility(
            visible = selection.isNotEmpty(),
            enter = slideInVertically { it * 2 },
            exit = slideOutVertically { it * 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            PrivateActionsBar(
                restoreLabel = restoreLabel,
                onRestore = model::restoreSelection,
                onDelete = { confirmDelete = true }
            )
        }

        LaunchedEffect(media) {
            if (null != media && items.isEmpty()) pagerOpen = false
        }
        AnimatedVisibility(
            visible = pagerOpen && items.isNotEmpty(),
            enter = fadeIn(tween(SHARED_ELEMENT_MS)),
            exit = fadeOut(tween(SHARED_ELEMENT_MS))
        ) {
            CompositionLocalProvider(LocalSharedElementVisible provides pagerOpen) {
                PrivateMediaPager(
                    items = items,
                    restoreLabel = restoreLabel,
                    initialIndex = pagerIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                    onClose = closePager,
                    onRestore = { model.restore(listOf(it)) },
                    onDelete = { model.delete(listOf(it)) }
                )
            }
        }
    }

    if (confirmDelete) {
        ConfirmDeleteDialog(
            text = stringResource(R.string.private_delete_selection_confirm, selection.size),
            onConfirm = {
                confirmDelete = false
                model.deleteSelection()
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun PrivateActionsBar(restoreLabel: String, onRestore: () -> Unit, onDelete: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(50),
        color = floatingPanelColor()
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            LabeledIconButton(
                label = restoreLabel,
                icon = painterResource(R.drawable.move),
                onClick = onRestore
            )
            LabeledIconButton(
                label = stringResource(R.string.delete),
                icon = painterResource(R.drawable.delete),
                onClick = onDelete
            )
        }
    }
}

@Composable
private fun ConfirmDeleteDialog(text: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = { Text(text) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(R.string.delete)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        modifier = Modifier.navigationBarsPadding()
    )
}

@Composable
private fun PrivateMediaPager(
    items: List<Media>,
    restoreLabel: String,
    initialIndex: Int,
    onClose: (page: Int) -> Unit,
    onRestore: (Media) -> Unit,
    onDelete: (Media) -> Unit,
) {
    val pagerState = rememberPagerState(initialIndex) { items.size }
    val dismissDrag = remember { DismissDragState() }
    val dragging by remember { derivedStateOf { dismissDrag.isDragging } }
    var showControls by remember { mutableStateOf(true) }
    val controlsVisible = showControls && !dragging
    var confirmDelete by remember { mutableStateOf<Media?>(null) }
    val close = { onClose(pagerState.currentPage) }
    val bottomGestureInset = WindowInsets.systemGestures.getBottom(LocalDensity.current)

    BackHandler(onBack = close)

    Box(
        Modifier
            .fillMaxSize()
            .drawBehind { drawRect(Color.Black.copy(alpha = 1f - dismissDrag.progress)) }) {
        CompositionLocalProvider(LocalDismissDrag provides dismissDrag) {
            HorizontalPager(
                state = pagerState,
                pageSpacing = 16.dp,
                modifier = Modifier.dragToDismiss(
                    state = dismissDrag,
                    onDismiss = close,
                    ignoreBottom = bottomGestureInset
                )
            ) { page ->
                val item = items[page]
                if (null != item.video) {
                    VideoView(
                        media = item,
                        isActive = page == pagerState.currentPage,
                        controlsVisible = controlsVisible,
                        controlsBottomPadding = 160.dp,
                        onTap = { showControls = !showControls }
                    )
                } else {
                    ImageView(item) { showControls = !showControls }
                }
            }
        }

        if (controlsVisible) {
            FloatingCircle(
                Modifier
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                IconButton(onClick = close) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.back)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = controlsVisible,
            enter = slideInVertically { it / 2 },
            exit = slideOutVertically { it / 2 },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            PrivateActionsBar(
                restoreLabel = restoreLabel,
                onRestore = { items.getOrNull(pagerState.currentPage)?.let(onRestore) },
                onDelete = { confirmDelete = items.getOrNull(pagerState.currentPage) }
            )
        }
    }

    confirmDelete?.let { media ->
        ConfirmDeleteDialog(
            text = stringResource(R.string.private_delete_confirm),
            onConfirm = {
                confirmDelete = null
                onDelete(media)
            },
            onDismiss = { confirmDelete = null }
        )
    }
}

/** Запрещает скриншоты и показ содержимого в списке недавних приложений */
@Composable
private fun SecureWindow() {
    val activity = LocalContext.current as? Activity ?: return
    DisposableEffect(activity) {
        activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
}

/** Личный альбом закрывается, когда приложение уходит в фон: вернуться можно только через проверку */
@Composable
private fun CloseWhenHidden(onClose: () -> Unit) {
    val activity = LocalContext.current as? Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val currentOnClose by rememberUpdatedState(onClose)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (Lifecycle.Event.ON_STOP == event && true != activity?.isChangingConfigurations) {
                currentOnClose()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
