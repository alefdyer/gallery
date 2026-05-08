package com.asinosoft.gallery.ui

import android.icu.text.DateFormatSymbols
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.data.AlbumWithCover
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.ui.component.AddToAlbumDialog
import com.asinosoft.gallery.ui.component.DragSelectionState
import com.asinosoft.gallery.ui.component.LazyGridVerticalScrollIndicator
import com.asinosoft.gallery.ui.component.MediaThumbnail
import com.asinosoft.gallery.ui.component.SelectionInfoBar
import com.asinosoft.gallery.ui.component.dragSelection

@Composable
fun ImageListView(
    albums: List<AlbumWithCover>,
    images: List<Media>,
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
    var closeOnEmptyList by remember { mutableStateOf(false) }
    var selection by remember { mutableStateOf(setOf<Long>()) }
    val selectionMode by remember { derivedStateOf { selection.isNotEmpty() } }
    var selectionBarHeight by remember { mutableIntStateOf(0) }
    var topPadding by remember { mutableIntStateOf(0) }
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

    LaunchedEffect(albumId, images, onClose) {
        if (closeOnEmptyList && images.isEmpty()) {
            onClose()
        } else {
            closeOnEmptyList = null != albumId
        }
    }

    LaunchedEffect(selectionMode) {
        topPadding = if (selectionMode) selectionBarHeight else 0

        val offset = if (selectionMode) selectionBarHeight else -selectionBarHeight
        lazyGridState.dispatchRawDelta(offset.toFloat())
    }

    Box(modifier) {
        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .padding(top = topPadding.pxToDp())
                .dragSelection(
                    items = images,
                    state = lazyGridState,
                    currentSelection = { selection },
                    dragSelectionState = dragSelectionState,
                    onSelectedChange = { selection = it }
                )
        ) {
            items(images, key = { it.id }) { media ->
                MediaThumbnail(
                    media = media,
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

        date?.let { date ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent.copy(0.5f), Color.Transparent)
                        )
                    )
            ) {
                Text(
                    text = date,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    modifier = Modifier.padding(4.dp)
                )
            }
        }

        LazyGridVerticalScrollIndicator(
            lazyGridState = lazyGridState,
            listItems = images,
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

private val months = DateFormatSymbols
    .getInstance()
    .getMonths(DateFormatSymbols.STANDALONE, DateFormatSymbols.WIDE)
