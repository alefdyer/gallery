package com.asinosoft.gallery.ui

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.GalleryViewModel
import com.asinosoft.gallery.ui.util.onSingleClick

@Composable
fun PagerView(
    items: List<Media>,
    modifier: Modifier = Modifier,
    current: Media? = null,
    model: GalleryViewModel = hiltViewModel(),
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    val offset = items.indexOf(current).coerceAtLeast(0)
    val pagerState: PagerState = rememberPagerState(offset) { items.count() }
    val currentItem by remember { derivedStateOf { items[pagerState.currentPage] } }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (Activity.RESULT_OK == it.resultCode) {
                model.deleteAll(listOf(currentItem))

                if (1 == items.count()) {
                    onClose()
                }
            }
        }

    Box(
        modifier =
            modifier
                .background(Color.Black),
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
                    .onSingleClick { showControls = !showControls }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, amount ->
                            if (amount > 10 && !showInfo) onClose()
                            if (amount < -10) showInfo = true
                        }
                    },
        ) { n ->
            val item = items[n]
            if (null !== item.image) {
                ImageView(item.uri)
            } else if (null != item.video) {
                VideoView(item.uri)
            } else {
                DummyView()
            }
        }

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(tween(easing = LinearEasing)),
            exit = slideOutVertically(tween(easing = LinearEasing)),
        ) {
            PagerViewBar(
                onBack = onClose,
                onShowInfo = { showInfo = true },
            )
        }

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = showControls,
            enter = slideInVertically(tween(easing = LinearEasing)) { it / 2 },
            exit = slideOutVertically(tween(easing = LinearEasing)) { it / 2 },
        ) {
            PagerBottomBar(
                onShare = { model.share(listOf(currentItem), context) },
                onEdit = { model.edit(currentItem, context) },
                onSearch = {},
                onDelete = { model.delete(listOf(currentItem), context, launcher) },
            )
        }

        BackHandler(onBack = onClose)
    }
}
