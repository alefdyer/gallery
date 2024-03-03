package com.asinosoft.gallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastMap
import com.asinosoft.gallery.model.GalleryViewModel
import java.util.logging.Logger

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagerView(
    viewModel: GalleryViewModel,
    position: Int,
    onClose: () -> Unit,
) {
    val images by viewModel.images.collectAsState(initial = listOf())
    val pagerState: PagerState = rememberPagerState(position) { images.count() }
    var isPagerEnabled by remember { mutableStateOf(true) }

    LaunchedEffect(key1 = position) {
        Logger.getLogger("app").info("Pager: position = $position")

    }

    Box(modifier = Modifier.background(Color.Black)) {
        HorizontalPager(state = pagerState,
            pageSpacing = 16.dp,
            userScrollEnabled = isPagerEnabled,
            modifier = Modifier.pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()

                        // Блокируем пейджер, когда работает мультитач (зум)
                        isPagerEnabled = event.changes.fastMap { it.id }.distinct().count() < 2
                    }
                }
            }) { n ->
            ImageView(image = images[n], canBeSwiped = { isPagerEnabled = it })
        }

        PagerViewBar(onClose)
    }
}
