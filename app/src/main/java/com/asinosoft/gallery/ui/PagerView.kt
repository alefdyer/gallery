package com.asinosoft.gallery.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.asinosoft.gallery.data.Image

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagerView(
    images: List<Image>,
    position: Int,
    onClose: () -> Unit
) {
    Surface {
        val pagerState = rememberPagerState(position) { images.count() }

        HorizontalPager(
            state = pagerState,
            reverseLayout = true
        ) {
            AsyncImage(
                model = images[it].url,
                contentDescription = "",
                contentScale = ContentScale.FillHeight,
                modifier = Modifier
                    .fillMaxHeight()
                    .clickable { onClose() }
            )
        }
    }
}
