package com.asinosoft.gallery.ui

import android.icu.text.DateFormatSymbols
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.ImageListViewModel
import com.asinosoft.gallery.ui.component.AddToAlbumDialog
import com.asinosoft.gallery.ui.component.DragSelectionState
import com.asinosoft.gallery.ui.component.LazyGridVerticalScrollIndicator
import com.asinosoft.gallery.ui.component.MediaThumbnail
import com.asinosoft.gallery.ui.component.SelectionControlBar
import com.asinosoft.gallery.ui.component.SelectionInfoBar
import com.asinosoft.gallery.ui.component.ShadowedHeader
import com.asinosoft.gallery.ui.component.dragSelection

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImageListView(
    onMediaClick: (Media) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    nestedScroll: NestedScrollConnection? = null,
    model: ImageListViewModel = hiltViewModel()
) {
    val images by model.filteredImages.collectAsState(listOf())
    val selection by model.selection.collectAsState()

    var closeOnEmptyList by remember { mutableStateOf(false) }
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

    LaunchedEffect(model.albumId, images, onClose) {
        if (closeOnEmptyList && images.isEmpty()) {
            onClose()
        } else {
            closeOnEmptyList = null != model.albumId
        }
    }

    BackHandler(selection.isNotEmpty(), model::clearSelection)

    Box(modifier.fillMaxSize()) {
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
                    onSelectedChange = model::setSelection
                )
                .let {
                    if (nestedScroll == null) it else it.nestedScroll(nestedScroll)
                }
        ) {
            items(images, key = { it.id }) { media ->
                MediaThumbnail(
                    media = media,
                    selectionMode = selection.isNotEmpty(),
                    selected = selection,
                    onClick = onMediaClick,
                    onSelect = { image ->
                        if (!dragSelectionState.active) {
                            model.toggleSelection(image)
                        }
                    }
                )
            }
        }

        date?.let { ShadowedHeader(it) }

        LazyGridVerticalScrollIndicator(
            lazyGridState = lazyGridState,
            listItems = images,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(top = topPadding.pxToDp(), end = 4.dp)
        )

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
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

@Composable
fun Int.pxToDp() = with(LocalDensity.current) { this@pxToDp.toDp() }

private val months = DateFormatSymbols
    .getInstance()
    .getMonths(DateFormatSymbols.STANDALONE, DateFormatSymbols.WIDE)
