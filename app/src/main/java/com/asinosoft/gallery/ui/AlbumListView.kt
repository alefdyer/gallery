package com.asinosoft.gallery.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumCategory
import com.asinosoft.gallery.data.AlbumWithCover
import com.asinosoft.gallery.data.CategoryWithAlbums
import com.asinosoft.gallery.data.isSystem
import com.asinosoft.gallery.data.name
import com.asinosoft.gallery.data.storage.Storage
import com.asinosoft.gallery.model.AlbumsViewModel
import com.asinosoft.gallery.ui.component.AlbumCover
import com.asinosoft.gallery.ui.component.ImageListHeaderBackground
import com.asinosoft.gallery.ui.component.ImageListHeaderTitle
import com.asinosoft.gallery.ui.component.NewAlbumCategoryDialog
import com.asinosoft.gallery.ui.component.NewAlbumPlaceholder
import com.asinosoft.gallery.ui.component.ScrollToTopButton
import com.asinosoft.gallery.ui.component.StorageCard
import com.google.gson.Gson
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs

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
    val storages by model.storages.collectAsState()
    val lazyListState = rememberLazyListState()
    var droppedAlbum by remember { mutableStateOf<Album?>(null) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var isDragActive by remember { mutableStateOf(false) }
    var dropCategory by remember { mutableStateOf<CategoryWithAlbums?>(null) }

    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val moveThreshold = with(density) { CATEGORY_MOVE_THRESHOLD.toPx() }
    var dragOrder by remember { mutableStateOf<List<Long>?>(null) }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }
    var dragMoved by remember { mutableStateOf(false) }
    var menuCategoryId by remember { mutableStateOf<Long?>(null) }
    var expandedCategoryIds by remember { mutableStateOf(emptySet<Long>()) }
    var renamingCategory by remember { mutableStateOf<AlbumCategory?>(null) }

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
        dragOrder = categories.map { it.category.id }
    }

    fun onCategoryDrag(dy: Float) {
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
                top < autoScrollTop ->
                    -autoScrollMaxSpeed * ((autoScrollTop - top) / autoScrollEdge).coerceAtMost(1f)

                top + titleHeight > bottomLimit ->
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
        val headerHeight = (contentPadding.calculateTopPadding() + rowHeight) / 2
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

                AlbumCategoriesRow(
                    category = category,
                    cardSize = cardSize,
                    highlighted = isDragActive && category == dropCategory,
                    onAlbumClick = onAlbumClick,
                    expanded = id in expandedCategoryIds,
                    onToggleExpand = {
                        expandedCategoryIds =
                            if (id in expandedCategoryIds) expandedCategoryIds - id
                            else expandedCategoryIds + id
                    },
                    menuExpanded = id == menuCategoryId,
                    onMenuDismiss = { menuCategoryId = null },
                    onRenameClick = {
                        menuCategoryId = null
                        renamingCategory = category.category
                    },
                    onDragStart = { onCategoryDragStart(id) },
                    onDrag = ::onCategoryDrag,
                    onDragEnd = ::onCategoryDragEnd,
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

            if (storages.isNotEmpty()) {
                item(key = STORAGES_KEY) {
                    SectionTitle(
                        title = stringResource(R.string.storages),
                        count = storages.size
                    )
                }

                items(storages, key = { "storage_${it.storage.id}" }) { info ->
                    Box(
                        Modifier
                            .padding(horizontal = SCREEN_PADDING, vertical = CARD_SPACING / 2)
                            .clip(RoundedCornerShape(16.dp))
                    ) {
                        StorageCard(
                            info = info,
                            onClick = { onStorageClick(info.storage) }
                        )
                    }
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
        NewAlbumCategoryDialog(
            title = stringResource(R.string.rename_category),
            initialName = category.name,
            confirmText = stringResource(R.string.save),
            onCreateCategory = { name -> model.renameCategory(category, name) },
            onDismiss = { renamingCategory = null }
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
    cardSize: Dp,
    highlighted: Boolean,
    onAlbumClick: (Album) -> Unit,
    expanded: Boolean,
    onToggleExpand: () -> Unit,
    menuExpanded: Boolean,
    onMenuDismiss: () -> Unit,
    onRenameClick: () -> Unit,
    onDragStart: () -> Unit,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
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
                title = category.category.name(),
                count = category.albums.size,
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

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = onMenuDismiss
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.rename)) },
                    enabled = !category.category.isSystem(),
                    leadingIcon = {
                        Icon(painterResource(R.drawable.edit), contentDescription = null)
                    },
                    onClick = onRenameClick
                )
            }
        }

        if (expandable && expanded) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CARD_SPACING),
                modifier = Modifier.padding(horizontal = SCREEN_PADDING - 4.dp)
            ) {
                category.albums.chunked(ALBUMS_PER_ROW).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(CARD_SPACING)) {
                        row.forEach { album ->
                            key(album.album.id) {
                                AlbumCard(album, cardSize, onAlbumClick)
                            }
                        }
                    }
                }
            }
        } else {
            val rowState = rememberLazyListState()
            val firstAlbumId = category.albums.firstOrNull()?.album?.id
            LaunchedEffect(firstAlbumId) {
                rowState.scrollToItem(0)
            }

            LazyRow(
                state = rowState,
                contentPadding = PaddingValues(horizontal = SCREEN_PADDING - 4.dp),
                horizontalArrangement = Arrangement.spacedBy(CARD_SPACING)
            ) {
                items(category.albums, { it.album.id }) { album ->
                    AlbumCard(album, cardSize, onAlbumClick)
                }
            }
        }
    }
}

@Composable
private fun AlbumCard(
    album: AlbumWithCover,
    cardSize: Dp,
    onAlbumClick: (Album) -> Unit,
) {
    AlbumCover(
        album,
        Modifier
            .size(cardSize)
            .dragAndDropSource { _ -> album.album.toDragAndDrop() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown()
                    withTimeoutOrNull(300) { waitForUpOrCancellation() }?.let {
                        onAlbumClick(album.album)
                    }
                }
            }
    )
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
    expanded: Boolean? = null,
    onToggleExpand: () -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(start = SCREEN_PADDING - 4.dp, end = SCREEN_PADDING - 4.dp, top = 12.dp, bottom = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
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
                contentDescription = if (expanded) "Свернуть" else "Развернуть",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = 4.dp)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onToggleExpand)
                    .padding(4.dp)
                    .size(24.dp)
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
private const val STORAGES_KEY = "storages"
private val HEADER_GAP = 2.dp
private const val ALBUMS_PER_ROW = 3
private val SCREEN_PADDING = 12.dp
private val CARD_SPACING = 8.dp
private val CATEGORY_MOVE_THRESHOLD = 12.dp
private val CATEGORY_TITLE_HEIGHT = 48.dp
private val AUTO_SCROLL_EDGE = 72.dp
private val AUTO_SCROLL_MAX_SPEED = 14.dp
