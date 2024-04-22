package com.asinosoft.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.data.Image

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagerView(
    images: List<Image>,
    image: Image? = null,
    onClose: () -> Unit,
) {
    val offset = images.indexOf(image).coerceAtLeast(0)
    val pagerState: PagerState = rememberPagerState(offset) { images.count() }

    Box(modifier = Modifier.background(Color.Black)) {
        HorizontalPager(state = pagerState, pageSpacing = 16.dp) { n ->
            ImageView(image = images[n])
        }

        PagerViewBar(onClose)

        BackHandler(onBack = onClose)
    }
}
