package com.asinosoft.photogallery.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.Album
import com.asinosoft.photogallery.data.AlbumCategory
import com.asinosoft.photogallery.data.AlbumWithCover
import com.asinosoft.photogallery.data.CategoryWithAlbums
import com.asinosoft.photogallery.data.isStorageCategory
import com.asinosoft.photogallery.data.isSystem
import com.asinosoft.photogallery.data.name
import com.asinosoft.photogallery.data.storageIdOrNull
import com.asinosoft.photogallery.data.storage.Storage
import com.asinosoft.photogallery.model.AlbumsViewModel
import com.asinosoft.photogallery.ui.component.AlbumCover
import com.asinosoft.photogallery.ui.component.FeatureTipCard
import com.asinosoft.photogallery.ui.component.FloatingDropdownMenu
import com.asinosoft.photogallery.ui.component.FloatingMenuItem
import com.asinosoft.photogallery.ui.component.ImageListHeaderBackground
import com.asinosoft.photogallery.ui.component.ImageListHeaderTitle
import com.asinosoft.photogallery.ui.component.NewAlbumCategoryDialog
import com.asinosoft.photogallery.ui.component.NewAlbumPlaceholder
import com.asinosoft.photogallery.ui.component.ScrollToTopButton
import com.asinosoft.photogallery.ui.component.TIP_ALBUM_CATEGORY
import com.asinosoft.photogallery.ui.component.TIP_ALBUM_DRAG
import com.asinosoft.photogallery.ui.component.drawableRes
import com.asinosoft.photogallery.ui.component.isTipSeen
import com.asinosoft.photogallery.ui.component.rememberFeatureTip
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.sign

@Composable
fun AlbumListView(
    modifier: Modifier = Modifier,
    onAlbumClick: (Album) -> Unit = {},
    onStorageClick: (Storage) -> Unit = {},
    nestedScroll: NestedScrollConnection,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    bottomPanelHidden: Boolean = false,
    model: AlbumsViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val categories by model.albums.collectAsState()
    val remoteStorages by model.remoteStorages.collectAsState()
    val fetchingStorageIds by model.fetchingStorageIds.collectAsState()
    val lazyListState = rememberLazyListState()
    var droppedAlbum by remember { mutableStateOf<Album?>(null) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var isDragActive by remember { mutableStateOf(false) }
    var dropCategory by remember { mutableStateOf<CategoryWithAlbums?>(null) }
    val tipContext = LocalContext.current
    val categoryTip = rememberFeatureTip(
        key = TIP_ALBUM_CATEGORY,
        enabled = categories.isNotEmpty(),
        delayMs = 700L
    )
    val albumDragTip = rememberFeatureTip(
        key = TIP_ALBUM_DRAG,
        enabled = categories.isNotEmpty() &&
                !categoryTip.visible &&
                isTipSeen(tipContext, TIP_ALBUM_CATEGORY),
        delayMs = 300L
    )

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val moveThreshold = with(density) { CATEGORY_MOVE_THRESHOLD.toPx() }
    var dragOrder by remember { mutableStateOf<List<Long>?>(null) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }
    var dragDirection by remember { mutableFloatStateOf(0f) }
    var menuCategoryId by remember { mutableStateOf<Long?>(null) }
    val expandedCategoryIds by model.expandedCategories.collectAsState()
    val alwaysExpandedCategoryIds by model.alwaysExpandedCategories.collectAsState()
    var renamingCategory by remember { mutableStateOf<AlbumCategory?>(null) }
    var renamingAlbum by remember { mutableStateOf<Album?>(null) }

    val shownCategories = dragOrder
        ?.mapNotNull { id -> categories.firstOrNull { it.category.id == id } }
        ?: categories

    LaunchedEffect(categories) {
        if (null == draggingId && categories.map { it.category.id } == dragOrder) {
            dragOrder = null
        }
    }

    fun onCategoryDragStart(id: Long) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        draggingId = id
        dragDelta = 0f
        dragMoved = false
        dragDirection = 0f
        dragOrder = categories.map { it.category.id }
    }

    fun onCategoryDrag(dy: Float) {
        if (0f != dy) dragDirection = sign(dy)
        dragDelta += dy
        if (abs(dragDelta) > moveThreshold) dragMoved = true

        val id = draggingId ?: return
        val order = dragOrder ?: return
        val visible = lazyListState.layoutInfo.visibleItemsInfo
        val position = visible.indexOfFirst { it.key == id }
        if (position < 0) return

        val current = visible[position]
        val center = current.offset + dragDelta + current.size / 2f
        val next = visible.getOrNull(position + 1)?.takeIf { it.key is Long }
        val previous = visible.getOrNull(position - 1)?.takeIf { it.key is Long }

        val target = when {
            null != next && center > next.offset + next.size / 2f -> next
            null != previous && center < previous.offset + previous.size / 2f -> previous
            else -> return
        }
        val from = order.indexOf(id)
        val to = order.indexOf(target.key as Long)
        if (from < 0 || to < 0) return

        dragOrder = order.toMutableList().apply {
            removeAt(from)
            add(to, id)
        }
        dragDelta += if (to > from) -target.size.toFloat() else target.size.toFloat()
    }

    fun onCategoryDragEnd() {
        val order = dragOrder
        if (dragMoved && null != order) {
            model.setCategoryOrder(order)
        } else {
            menuCategoryId = draggingId
            dragOrder = null
        }
        draggingId = null
        dragDelta = 0f
    }

    val autoScrollTop = with(density) { (contentPadding.calculateTopPadding() + AUTO_SCROLL_EDGE).toPx() }
    val autoScrollBottom = with(density) { (contentPadding.calculateBottomPadding() + AUTO_SCROLL_EDGE).toPx() }
    val autoScrollEdge = with(density) { AUTO_SCROLL_EDGE.toPx() }
    val autoScrollMaxSpeed = with(density) { AUTO_SCROLL_MAX_SPEED.toPx() }
    val titleHeight = with(density) { CATEGORY_TITLE_HEIGHT.toPx() }

    LaunchedEffect(draggingId) {
        val id = draggingId ?: return@LaunchedEffect
        while (true) {
            withFrameNanos { }
            val layoutInfo = lazyListState.layoutInfo
            val item = layoutInfo.visibleItemsInfo.firstOrNull { it.key == id } ?: continue
            val top = item.offset + dragDelta
            val bottomLimit = layoutInfo.viewportSize.height - autoScrollBottom

            val speed = when {
                !dragMoved -> 0f

                dragDirection < 0f && top < autoScrollTop ->
                    -autoScrollMaxSpeed * ((autoScrollTop - top) / autoScrollEdge).coerceAtMost(1f)

                dragDirection > 0f && top + titleHeight > bottomLimit ->
                    autoScrollMaxSpeed * ((top + titleHeight - bottomLimit) / autoScrollEdge).coerceAtMost(1f)

                else -> 0f
            }
            if (0f == speed) continue

            val consumed = lazyListState.scrollBy(speed)
            if (0f != consumed) {
                dragDelta += consumed
                onCategoryDrag(0f)
            }
        }
    }

    val albumCount = categories.sumOf { it.albums.size }
    val cover = remember(categories) {
        categories
            .flatMap { it.albums }
            .filter { null != it.cover }
            .maxByOrNull { it.album.date }
            ?.cover
    }

    fun categoryAt(event: DragAndDropEvent): CategoryWithAlbums? {
        val key = lazyListState.keyAt(event) as? Long ?: return null
        return categories.firstOrNull { it.category.id == key }
    }

    val dragAndDropTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                super.onStarted(event)

                isDragActive = true
                dropCategory = categoryAt(event)
            }

            override fun onMoved(event: DragAndDropEvent) {
                super.onMoved(event)

                dropCategory = categoryAt(event)

                val y = event.toAndroidDragEvent().y
                scope.launch {
                    val threshold = lazyListState.layoutInfo.viewportSize.height / 8f

                    if (y > lazyListState.layoutInfo.viewportEndOffset - threshold) {
                        lazyListState.scrollBy(threshold)
                    } else if (y < lazyListState.layoutInfo.viewportStartOffset + threshold) {
                        lazyListState.scrollBy(-threshold)
                    }
                }
            }

            override fun onEnded(event: DragAndDropEvent) {
                super.onEnded(event)

                isDragActive = false
                dropCategory = null
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                event.asAlbum()?.let { album ->
                    val category = categoryAt(event)

                    if (category == null) {
                        droppedAlbum = album
                        showNewCategoryDialog = true
                    } else {
                        model.moveAlbumIntoCategory(album, category.category)
                    }
                }

                return true
            }
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val rowHeight = (maxWidth - HEADER_GAP * 2) / 3
        val headerHeight = (contentPadding.calculateTopPadding() + rowHeight) / 2 * 1.5f
        val cardSize = (maxWidth - SCREEN_PADDING * 2 - CARD_SPACING * (ALBUMS_PER_ROW - 1)) / ALBUMS_PER_ROW

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 96.dp),
            modifier = Modifier
                .fillMaxSize()
                .dragAndDropTarget(
                    shouldStartDragAndDrop = {
                        it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_INTENT)
                    },
                    target = dragAndDropTarget
                )
                .nestedScroll(nestedScroll)
        ) {
            item(key = HEADER_KEY) {
                if (null == cover) {
                    Spacer(Modifier.height(contentPadding.calculateTopPadding()))
                } else {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(headerHeight)
                            .padding(bottom = HEADER_GAP)
                    ) {
                        ImageListHeaderBackground(cover, Modifier.fillMaxSize())
                        ImageListHeaderTitle(
                            title = stringResource(R.string.albums),
                            icon = R.drawable.album,
                            count = albumCount,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }

            items(shownCategories, key = { it.category.id }) { category ->
                val id = category.category.id
                val isDragged = id == draggingId
                val surface = MaterialTheme.colorScheme.background
                val isFirstCategory = category == shownCategories.firstOrNull()

                val storageInfo = category.category.storageIdOrNull()
                    ?.let { remoteStorages[it] }
                AlbumCategoriesRow(
                    category = category,
                    title = storageInfo?.title ?: category.category.name(),
                    titleIcon = storageInfo?.storage?.type?.drawableRes(),
                    cardSize = cardSize,
                    highlighted = isDragActive && category == dropCategory,
                    onAlbumClick = onAlbumClick,
                    expanded = id in expandedCategoryIds,
                    alwaysExpanded = id in alwaysExpandedCategoryIds,
                    onToggleExpand = { model.toggleCategoryExpanded(id) },
                    onAlwaysExpandedChange = { always ->
                        menuCategoryId = null
                        model.setCategoryAlwaysExpanded(id, always)
                    },
                    showRefresh = null != storageInfo,
                    onRefreshClick = {
                        menuCategoryId = null
                        model.refreshCategoryStorage(category.category)
                    },
                    menuExpanded = id == menuCategoryId,
                    onMenuDismiss = { menuCategoryId = null },
                    onRenameClick = {
                        menuCategoryId = null
                        renamingCategory = category.category
                    },
                    onAlbumRenameClick = { album -> renamingAlbum = album },
                    onDragStart = { onCategoryDragStart(id) },
                    onDrag = ::onCategoryDrag,
                    onDragEnd = ::onCategoryDragEnd,
                    showLoading = storageInfo?.storage?.id in fetchingStorageIds,
                    showCategoryTip = isFirstCategory && categoryTip.visible,
                    onCategoryTipDismiss = categoryTip.dismiss,
                    showAlbumDragTip = isFirstCategory && albumDragTip.visible,
                    onAlbumDragTipDismiss = albumDragTip.dismiss,
                    modifier = if (isDragged) {
                        Modifier
                            .zIndex(1f)
                            .graphicsLayer {
                                translationY = dragDelta
                                shadowElevation = 12.dp.toPx()
                                shape = RoundedCornerShape(16.dp)
                                clip = true
                            }
                            .background(surface)
                    } else {
                        Modifier.animateItem()
                    }
                )
            }

            if (isDragActive) {
                item(key = NEW_CATEGORY_KEY) {
                    NewAlbumCategory(
                        cardSize = cardSize,
                        highlighted = null == dropCategory
                    )
                }
            }
        }

        val scrolledAway by remember {
            derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
        }
        ScrollToTopButton(
            visible = bottomPanelHidden && scrolledAway,
            onClick = { scope.launch { lazyListState.animateScrollToItem(0) } },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = contentPadding.calculateBottomPadding() + 24.dp)
        )
    }

    renamingCategory?.let { category ->
        val storageId = category.storageIdOrNull()
        val storageInfo = storageId?.let { remoteStorages[it] }
        val defaultTitle = storageId?.let { model.defaultStorageCategoryTitle(it) }
        NewAlbumCategoryDialog(
            title = stringResource(R.string.rename_category),
            initialName = storageInfo?.title ?: category.name,
            confirmText = stringResource(R.string.save),
            resetToName = defaultTitle,
            onCreateCategory = { name -> model.renameCategory(category, name) },
            onDismiss = { renamingCategory = null }
        )
    }

    renamingAlbum?.let { album ->
        NewAlbumCategoryDialog(
            title = stringResource(R.string.rename_album),
            initialName = album.name,
            confirmText = stringResource(R.string.save),
            onCreateCategory = { name -> model.renameAlbum(album, name) },
            onDismiss = { renamingAlbum = null }
        )
    }

    if (showNewCategoryDialog) {
        NewAlbumCategoryDialog(
            onCreateCategory = { categoryName ->
                droppedAlbum?.let { album ->
                    model.moveAlbumIntoNewCategory(album, categoryName)
                }
            },
            onDismiss = {
                showNewCategoryDialog = false
            }
        )
    }
}

@Composable
private fun AlbumCategoriesRow(
    category: CategoryWithAlbums,
    title: String,
    titleIcon: Int? = null,
    cardSize: Dp,
    highlighted: Boolean,
    onAlbumClick: (Album) -> Unit,
    expanded: Boolean,
    alwaysExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAlwaysExpandedChange: (Boolean) -> Unit,
    showRefresh: Boolean = false,
    onRefreshClick: () -> Unit = {},
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onRenameClick: () -> Unit,
    onAlbumRenameClick: (Album) -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    showLoading: Boolean = false,
    showCategoryTip: Boolean = false,
    onCategoryTipDismiss: () -> Unit = {},
    showAlbumDragTip: Boolean = false,
    onAlbumDragTipDismiss: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .dropHighlight(highlighted)
            .padding(bottom = 8.dp)
    ) {
        val expandable = category.albums.size > ALBUMS_PER_ROW

        Box {
            SectionTitle(
                title = title,
                titleIcon = titleIcon,
                count = category.albums.size,
                titleMaxWidth = cardSize * 2 + CARD_SPACING,
                showLoading = showLoading,
                expanded = expanded.takeIf { expandable },
                onToggleExpand = onToggleExpand,
                modifier = Modifier.pointerInput(category.category.id) {
                    detectDragGesturesAfterLongPress(
                        onDragStart = { onDragStart() },
                        onDrag = { change, amount ->
                            change.consume()
                            onDrag(amount.y)
                        },
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd
                    )
                }
            )

            FloatingDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onMenuDismiss
            ) {
                FloatingMenuItem(
                    text = stringResource(R.string.rename),
                    enabled = !category.category.isSystem() ||
                            category.category.isStorageCategory(),
                    onClick = onRenameClick
                )
                if (expandable) {
                    FloatingMenuItem(
                        text = stringResource(
                            if (alwaysExpanded) R.string.always_collapsed else R.string.always_expanded
                        ),
                        onClick = { onAlwaysExpandedChange(!alwaysExpanded) }
                    )
                }
                if (showRefresh) {
                    FloatingMenuItem(
                        text = stringResource(R.string.storage_refresh),
                        onClick = onRefreshClick
                    )
                }
            }

            if (showCategoryTip) {
                FeatureTipCard(
                    text = stringResource(R.string.tip_album_category),
                    onDismiss = onCategoryTipDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 40.dp)
                )
            }
        }

        Box {
            if (expandable && expanded) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(CARD_SPACING),
                    modifier = Modifier.padding(horizontal = SCREEN_PADDING - 4.dp)
                ) {
                    category.albums.chunked(ALBUMS_PER_ROW).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(CARD_SPACING)) {
                            row.forEach { album ->
                                key(album.album.id) {
                                    AlbumCard(
                                        album = album,
                                        cardSize = cardSize,
                                        onAlbumClick = onAlbumClick,
                                        onRenameClick = onAlbumRenameClick
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                val rowState = rememberLazyListState()
                // Сбрасываем горизонтальный скролл только если сменился первый альбом
                // (перестановка/новые данные). Не трогаем при возврате из альбома —
                // иначе LaunchedEffect снова крутит к 0 и затирает rememberSaveable.
                val firstAlbumId = category.albums.firstOrNull()?.album?.id
                var seenFirstAlbumId by rememberSaveable(category.category.id) {
                    mutableStateOf(firstAlbumId)
                }
                LaunchedEffect(firstAlbumId) {
                    if (firstAlbumId != seenFirstAlbumId) {
                        seenFirstAlbumId = firstAlbumId
                        rowState.scrollToItem(0)
                    }
                }

                LazyRow(
                    state = rowState,
                    contentPadding = PaddingValues(horizontal = SCREEN_PADDING - 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(CARD_SPACING)
                ) {
                    items(category.albums, { it.album.id }) { album ->
                        AlbumCard(
                            album = album,
                            cardSize = cardSize,
                            onAlbumClick = onAlbumClick,
                            onRenameClick = onAlbumRenameClick
                        )
                    }
                }
            }

            if (showAlbumDragTip) {
                FeatureTipCard(
                    text = stringResource(R.string.tip_album_drag),
                    onDismiss = onAlbumDragTipDismiss,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 16.dp, top = 8.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumCard(
    album: AlbumWithCover,
    cardSize: Dp,
    onAlbumClick: (Album) -> Unit,
    onRenameClick: (Album) -> Unit,
) {
    var menuExpanded by remember(album.album.id) { mutableStateOf(false) }
    // Как у категорий: меню всегда; переименование — не для системных категорий
    val canRename = album.album.userCreated || !album.category.isSystem()
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val moveThreshold = with(density) { CATEGORY_MOVE_THRESHOLD.toPx() }

    Box {
        AlbumCover(
            album,
            Modifier
                .size(cardSize)
                .dragAndDropSource(
                    block = {
                        var transferred = false
                        var dragDistance = 0f
                        detectDragGesturesAfterLongPress(
                            onDragStart = {
                                transferred = false
                                dragDistance = 0f
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                dragDistance += abs(amount.x) + abs(amount.y)
                                if (!transferred && dragDistance > moveThreshold) {
                                    transferred = true
                                    startTransfer(album.album.toDragAndDrop())
                                }
                            },
                            onDragEnd = {
                                if (!transferred) menuExpanded = true
                            },
                            onDragCancel = {
                                if (!transferred) menuExpanded = true
                            }
                        )
                    }
                )
                .pointerInput(album.album.id) {
                    detectTapGestures(onTap = { onAlbumClick(album.album) })
                }
        )

        FloatingDropdownMenu(
            expanded = menuExpanded,
            onDismissRequest = { menuExpanded = false }
        ) {
            FloatingMenuItem(
                text = stringResource(R.string.rename),
                enabled = canRename,
                onClick = {
                    menuExpanded = false
                    onRenameClick(album.album)
                }
            )
        }
    }
}

@Composable
private fun NewAlbumCategory(
    cardSize: Dp,
    highlighted: Boolean,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp)
            .dropHighlight(highlighted)
            .padding(bottom = 8.dp)
    ) {
        SectionTitle(title = stringResource(R.string.new_category))
        NewAlbumPlaceholder(
            Modifier
                .padding(horizontal = SCREEN_PADDING - 4.dp)
                .size(cardSize)
        )
    }
}

@Composable
private fun SectionTitle(
    title: String,
    count: Int? = null,
    modifier: Modifier = Modifier,
    titleIcon: Int? = null,
    titleMaxWidth: Dp? = null,
    showLoading: Boolean = false,
    expanded: Boolean? = null,
    onToggleExpand: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(
                start = SCREEN_PADDING - 4.dp,
                end = SCREEN_PADDING - 4.dp,
                top = 12.dp,
                bottom = 8.dp
            )
    ) {
        if (null != titleIcon) {
            Image(
                painter = painterResource(titleIcon),
                contentDescription = null,
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(24.dp)
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f, fill = false)
                .then(if (titleMaxWidth != null) Modifier.widthIn(max = titleMaxWidth) else Modifier)
        )
        if (null != count) {
            Text(
                text = " ($count)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (null != expanded) {
            Icon(
                painter = painterResource(
                    if (expanded) R.drawable.expand_more else R.drawable.chevron_right
                ),
                contentDescription = stringResource(
                    if (expanded) R.string.collapse else R.string.expand
                ),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onToggleExpand)
                    .padding(4.dp)
                    .size(24.dp)
            )
        }
        if (showLoading) {
            CircularProgressIndicator(
                modifier = Modifier
                    .padding(start = 4.dp)
                    .size(18.dp),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
private fun Modifier.dropHighlight(highlighted: Boolean): Modifier =
    if (highlighted) {
        border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(16.dp))
    } else {
        this
    }

private fun Album.toDragAndDrop() =
    DragAndDropTransferData(
        clipData = ClipData.newIntent(
            "album.to.category",
            Intent("categorize.album").apply {
                putExtra("album", Gson().toJson(this@toDragAndDrop))
            }
        )
    )

private fun DragAndDropEvent.asAlbum(): Album? =
    if (mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_INTENT) && null != toAndroidDragEvent().clipData) {
        (0 until toAndroidDragEvent().clipData.itemCount)
            .map { toAndroidDragEvent().clipData.getItemAt(it) }
            .firstNotNullOfOrNull {
                Gson().fromJson(it.intent.getStringExtra("album"), Album::class.java)
            }
    } else {
        null
    }

private fun LazyListState.keyAt(event: DragAndDropEvent): Any? {
    val y = event.toAndroidDragEvent().y

    return layoutInfo.visibleItemsInfo.firstOrNull { item ->
        y >= item.offset && y <= item.offset + item.size
    }?.key
}

private const val HEADER_KEY = "header"
private const val NEW_CATEGORY_KEY = "new_category"
private val HEADER_GAP = 2.dp
private const val ALBUMS_PER_ROW = 3
private val SCREEN_PADDING = 12.dp
private val CARD_SPACING = 8.dp
private val CATEGORY_MOVE_THRESHOLD = 12.dp
private val CATEGORY_TITLE_HEIGHT = 48.dp
private val AUTO_SCROLL_EDGE = 72.dp
private val AUTO_SCROLL_MAX_SPEED = 14.dp
