package com.asinosoft.gallery.ui

import android.app.Activity
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.GalleryApp
import com.asinosoft.gallery.data.HeaderItem
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.data.MediaItem
import com.asinosoft.gallery.data.groupByMonth
import com.asinosoft.gallery.model.GalleryViewModel
import com.asinosoft.gallery.ui.component.GroupHeader
import com.asinosoft.gallery.ui.component.GroupItem
import com.asinosoft.gallery.ui.component.SelectionInfoBar

@Composable
fun ImageListView(
    media: List<Media>,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onClick: (Media) -> Unit = {},
    model: GalleryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val items by remember(media) { mutableStateOf(media.groupByMonth()) }
    var selected by remember { mutableStateOf<Set<Media>>(setOf()) }
    val selectionMode by remember { derivedStateOf { selected.isNotEmpty() } }
    var selectionBarHeight by remember { mutableIntStateOf(0) }
    var topPadding by remember { mutableIntStateOf(0) }
    val lazyGridState = rememberLazyGridState()

    val deleter =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            Log.d(GalleryApp.TAG, "Test: ${it.data?.getBooleanExtra("test", false)}")
            if (Activity.RESULT_OK == it.resultCode) {
                model.deleteAll(selected)

                if (selected.count() == media.count()) {
                    onClose()
                } else {
                    selected = setOf()
                }
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
            modifier = Modifier.padding(top = topPadding.pxToDp()),
        ) {
            items(
                items,
                span = {
                    when (it) {
                        is HeaderItem -> GridItemSpan(maxLineSpan)
                        else -> GridItemSpan(1)
                    }
                },
            ) {
                when (it) {
                    is HeaderItem -> {
                        GroupHeader(it)
                    }

                    is MediaItem -> {
                        GroupItem(
                            media = it.media,
                            selectionMode = selectionMode,
                            selected = selected,
                            onClick = onClick,
                            onSelect = { image ->
                                if (selected.contains(image)) {
                                    selected -= image
                                } else {
                                    selected += image
                                }
                            },
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = selectionMode) {
            SelectionInfoBar(
                selected = selected,
                modifier = Modifier.onSizeChanged { selectionBarHeight = it.height },
                onBack = {
                    selected = setOf()
                },
                onShare = { model.share(it, context) },
                onDelete = { model.delete(it, context, deleter) },
            )
        }
    }
}

@Composable
fun Int.pxToDp() = with(LocalDensity.current) { this@pxToDp.toDp() }
