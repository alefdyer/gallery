package com.asinosoft.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.model.PagerViewModel

@Composable
fun PagerView(
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    model: PagerViewModel = hiltViewModel()
) {
    val items by model.images.collectAsState(listOf())
    val offset by model.offset.collectAsState(0)
    val pagerState: PagerState = key(items, offset) {
        rememberPagerState(offset) { items.size }
    }
    val currentItem by remember(items) { derivedStateOf { items[pagerState.currentPage] } }

    Box(
        modifier =
            modifier
                .background(Color.Black)
    ) {
        var showControls by remember { mutableStateOf(true) }
        var showInfo by remember { mutableStateOf(false) }

        if (showInfo) {
            MediaInfoSheet(currentItem) { showInfo = false }
        }

        HorizontalPager(
            state = pagerState,
            pageSpacing = 16.dp,
            modifier =
                Modifier
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, amount ->
                            if (amount > 10 && !showInfo) onClose()
                            if (amount < -10) showInfo = true
                        }
                    }
        ) { n ->
            val item = items[n]
            if (null !== item.image) {
                ImageView(item) { showControls = !showControls }
            } else if (null != item.video) {
                VideoView(item) { isPlaying -> showControls = !isPlaying }
            } else {
                DummyView()
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(tween(easing = LinearEasing)),
            exit = slideOutVertically(tween(easing = LinearEasing))
        ) {
            PagerViewBar(
                onBack = onClose,
                onShowInfo = { showInfo = true }
            )
        }

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = showControls,
            enter = slideInVertically(tween(easing = LinearEasing)) { it / 2 },
            exit = slideOutVertically(tween(easing = LinearEasing)) { it / 2 }
        ) {
            PagerBottomBar(
                onShare = { model.share(currentItem) },
                onEdit = { model.edit(currentItem) },
                onSearch = {},
                onDelete = {
                    model.delete(currentItem) {
                        if (1 == items.count()) {
                            onClose()
                        }
                    }
                }
            )
        }

        BackHandler(onBack = onClose)
    }
}
