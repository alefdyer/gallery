package com.asinosoft.gallery.ui.component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Media
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlin.math.abs

@Composable
fun Carousel(
    items: List<Media>,
    pagerState: PagerState,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val listState = rememberLazyListState()
    val isDragged by listState.interactionSource.collectIsDraggedAsState()
    var userScroll by remember { mutableStateOf(false) }

    fun centerOffset(index: Int): Int =
        with(density) { items[index].carouselWidth().roundToPx() / 2 }

    fun centerOn(index: Int, animate: Boolean) {
        if (index !in items.indices) return
        scope.launch {
            if (animate) {
                listState.animateScrollToItem(index, centerOffset(index))
            } else {
                listState.scrollToItem(index, centerOffset(index))
            }
        }
    }

    LaunchedEffect(isDragged) {
        if (isDragged) userScroll = true
    }

    LaunchedEffect(items) {
        centerOn(pagerState.currentPage, animate = false)
    }

    LaunchedEffect(pagerState, items) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            if (!userScroll) centerOn(page, animate = true)
        }
    }

    LaunchedEffect(listState, pagerState) {
        snapshotFlow { if (userScroll) listState.centeredIndex() else null }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { index ->
                if (index != pagerState.currentPage) pagerState.scrollToPage(index)
            }
    }

    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { scrolling ->
            if (!scrolling && userScroll) {
                userScroll = false
                val index = listState.centeredIndex() ?: pagerState.currentPage
                if (index != pagerState.currentPage) pagerState.scrollToPage(index)
                centerOn(index, animate = true)
            }
        }
    }

    BoxWithConstraints(modifier) {
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(horizontal = maxWidth / 2),
            horizontalArrangement = Arrangement.spacedBy(ITEM_SPACING),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxSize()
        ) {
            itemsIndexed(items, key = { _, media -> media.id }) { page, media ->
                val scale by animateFloatAsState(
                    targetValue = if (page == pagerState.currentPage) ACTIVE_SCALE else 1f,
                    label = "carouselScale"
                )
                val width = media.carouselWidth()

                Box(
                    Modifier
                        .size(width, ITEM_HEIGHT)
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            shape = RoundedCornerShape(4.dp)
                            clip = true
                        }
                ) {
                    MediaThumbnail(
                        media = media,
                        aspectRatio = width / ITEM_HEIGHT,
                        showVideoBadge = false,
                        onClick = { scope.launch { pagerState.scrollToPage(page) } },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (null != media.video) {
                        Icon(
                            painter = painterResource(R.drawable.play_arrow),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(2.dp)
                                .size(14.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun Media.carouselWidth(): Dp = if (null != video) VIDEO_WIDTH else PHOTO_WIDTH

private fun LazyListState.centeredIndex(): Int? {
    val info = layoutInfo
    val center = (info.viewportStartOffset + info.viewportEndOffset) / 2
    return info.visibleItemsInfo
        .minByOrNull { abs(it.offset + it.size / 2 - center) }
        ?.index
}

private val ITEM_HEIGHT = 44.dp
private val PHOTO_WIDTH = 30.dp
private val VIDEO_WIDTH = 66.dp
private val ITEM_SPACING = 8.dp
private const val ACTIVE_SCALE = 1.2f
