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
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.model.GalleryViewModel
import com.asinosoft.gallery.ui.util.onSingleClick

@Composable
fun PagerView(
    images: List<Image>,
    modifier: Modifier = Modifier,
    startImage: Image? = null,
    model: GalleryViewModel = hiltViewModel(),
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    val offset = images.indexOf(startImage).coerceAtLeast(0)
    val pagerState: PagerState = rememberPagerState(offset) { images.count() }
    val currentImage by remember { derivedStateOf { images[pagerState.currentPage] } }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (Activity.RESULT_OK == it.resultCode) {
                model.deleteAll(listOf(currentImage))

                if (1 == images.count()) {
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
        var showImageInfo by remember { mutableStateOf(false) }

        if (showImageInfo) {
            ImageInfoSheet(currentImage) { showImageInfo = false }
        }

        HorizontalPager(
            state = pagerState,
            pageSpacing = 16.dp,
            modifier =
                Modifier
                    .onSingleClick { showControls = !showControls }
                    .pointerInput(Unit) {
                        detectVerticalDragGestures { _, amount ->
                            if (amount > 10 && !showImageInfo) onClose()
                            if (amount < -10) showImageInfo = true
                        }
                    },
        ) { n ->
            ImageView(image = images[n])
        }

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(tween(easing = LinearEasing)),
            exit = slideOutVertically(tween(easing = LinearEasing)),
        ) {
            PagerViewBar(
                onBack = onClose,
                onShowImageInfo = { showImageInfo = true },
            )
        }

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = showControls,
            enter = slideInVertically(tween(easing = LinearEasing)) { it / 2 },
            exit = slideOutVertically(tween(easing = LinearEasing)) { it / 2 },
        ) {
            PagerBottomBar(
                onShare = { model.share(listOf(currentImage), context) },
                onEdit = { model.edit(currentImage, context) },
                onSearch = {},
                onDelete = { model.delete(listOf(currentImage), context, launcher) },
            )
        }

        BackHandler(onBack = onClose)
    }
}
