package com.asinosoft.gallery.ui

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
    position: Int,
    onClose: () -> Unit,
) {
    val pagerState: PagerState = rememberPagerState(position) { images.count() }

    Box(modifier = Modifier.background(Color.Black)) {
        HorizontalPager(state = pagerState, pageSpacing = 16.dp) { n ->
            ImageView(image = images[n])
        }

        PagerViewBar(onClose)
    }
}
