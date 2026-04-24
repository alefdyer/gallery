package com.asinosoft.gallery.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.items
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.HeaderItem
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.MediaItem
import com.asinosoft.gallery.data.groupByMonth
import com.asinosoft.gallery.ui.component.AddToAlbumDialog
import com.asinosoft.gallery.ui.component.DragSelectionState
import com.asinosoft.gallery.ui.component.GroupHeader
import com.asinosoft.gallery.ui.component.GroupItem
import com.asinosoft.gallery.ui.component.LazyGridVerticalScrollIndicator
import com.asinosoft.gallery.ui.component.SelectionInfoBar
import com.asinosoft.gallery.ui.component.dragSelection

@Composable
fun ImageListView(
    albums: List<Album>,
    media: List<Media>,
    onClick: (Media) -> Unit,
    onShare: (Set<Long>) -> Unit,
    onDelete: (Set<Long>, () -> Unit) -> Unit,
    onAddTag: (Set<Long>, Long) -> Unit,
    onCreateTag: (Set<Long>, String) -> Unit,
    onRemoveTag: (Set<Long>, Long) -> Unit,
    modifier: Modifier = Modifier,
    albumId: Long? = null,
    onClose: () -> Unit = {}
) {
    val items by remember(media) { mutableStateOf(media.groupByMonth()) }
    var selection by remember { mutableStateOf(setOf<Long>()) }
    val selectionMode by remember { derivedStateOf { selection.isNotEmpty() } }
    var selectionBarHeight by remember { mutableIntStateOf(0) }
    var topPadding by remember { mutableIntStateOf(0) }
    val lazyGridState = rememberLazyStaggeredGridState()
    var showTagDialog by remember { mutableStateOf(false) }
    val dragSelectionState = remember { DragSelectionState() }

    LaunchedEffect(media, onClose) {
        if (media.isEmpty()) {
            onClose()
        }
    }

    LaunchedEffect(selectionMode) {
        topPadding = if (selectionMode) selectionBarHeight else 0

        val offset = if (selectionMode) selectionBarHeight else -selectionBarHeight
        lazyGridState.dispatchRawDelta(offset.toFloat())
    }

    Box(modifier) {
        LazyVerticalStaggeredGrid(
            state = lazyGridState,
            columns = StaggeredGridCells.Fixed(3),
            modifier = Modifier
                .padding(top = topPadding.pxToDp())
                .dragSelection(
                    items = items,
                    state = lazyGridState,
                    currentSelection = { selection },
                    dragSelectionState = dragSelectionState,
                    onSelectedChange = { selection = it }
                )
        ) {
            items(
                items,
                span = {
                    when (it) {
                        is HeaderItem -> StaggeredGridItemSpan.FullLine
                        else -> StaggeredGridItemSpan.SingleLane
                    }
                }
            ) {
                when (it) {
                    is HeaderItem -> {
                        val allSelected =
                            it.mediaIds.isNotEmpty() && selection.containsAll(it.mediaIds)

                        GroupHeader(
                            header = it,
                            selectionMode = selectionMode,
                            allSelected = allSelected,
                            onSelectGroup = {
                                selection = if (allSelected) {
                                    selection - it.mediaIds
                                } else {
                                    selection + it.mediaIds
                                }
                            }
                        )
                    }

                    is MediaItem -> {
                        GroupItem(
                            media = it.media,
                            selectionMode = selectionMode,
                            selected = selection,
                            onClick = onClick,
                            onSelect = { image ->
                                if (!dragSelectionState.active) {
                                    selection = if (selection.contains(image.id)) {
                                        selection - image.id
                                    } else {
                                        selection + image.id
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }

        LazyGridVerticalScrollIndicator(
            lazyGridState = lazyGridState,
            listItems = items,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = topPadding.pxToDp(), end = 4.dp)
        )

        AnimatedVisibility(visible = selectionMode) {
            SelectionInfoBar(
                selected = selection,
                modifier = Modifier.onSizeChanged { selectionBarHeight = it.height },
                onBack = { selection = setOf() },
                onShare = { onShare(selection) },
                onDelete = { onDelete(selection) { selection = setOf() } },
                onAddTag = { showTagDialog = true },
                onRemoveTag = albumId?.let { { onRemoveTag(selection, it) } }
            )
        }

        if (showTagDialog) {
            AddToAlbumDialog(
                albums = albums,
                onPickAlbum = { album ->
                    onAddTag(selection, album.id)
                    selection = setOf()
                    showTagDialog = false
                },
                onCreateAlbum = { name ->
                    onCreateTag(selection, name)
                    selection = setOf()
                    showTagDialog = false
                },
                onDismiss = { showTagDialog = false }
            )
        }
    }
}

@Composable
fun Int.pxToDp() = with(LocalDensity.current) { this@pxToDp.toDp() }
