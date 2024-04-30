package com.asinosoft.gallery.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.util.fastAll
import com.asinosoft.gallery.data.Image

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PagerView(
    images: List<Image>,
    image: Image? = null,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val offset = images.indexOf(image).coerceAtLeast(0)
    val pagerState: PagerState = rememberPagerState(offset) { images.count() }
    var showControls by remember { mutableStateOf(true) }

    Box(
        modifier = Modifier
            .background(Color.Black),
    ) {
        HorizontalPager(
            state = pagerState,
            pageSpacing = 16.dp,
            modifier = Modifier.onSingleClick { showControls = !showControls },
        ) { n ->
            ImageView(image = images[n])
        }

        AnimatedVisibility(
            visible = showControls,
            enter = slideInVertically(tween(easing = LinearEasing)),
            exit = slideOutVertically(tween(easing = LinearEasing)),
        ) {
            PagerViewBar(onBack = onClose)
        }

        AnimatedVisibility(
            modifier = Modifier.align(Alignment.BottomCenter),
            visible = showControls,
            enter = slideInVertically(tween(easing = LinearEasing)) { it / 2 },
            exit = slideOutVertically(tween(easing = LinearEasing)) { it / 2 },
        ) {
            PagerBottomBar(
                onShare = { image?.let { share(context, it) } },
                onEdit = {},
                onSearch = {},
                onDelete = {},
            )
        }

        BackHandler(onBack = onClose)
    }
}

internal fun Modifier.onSingleClick(onClick: () -> Unit): Modifier = this then pointerInput(Unit) {
    awaitPointerEventScope {
        while (true) {
            val down = awaitFirstDown(false)

            if (awaitPointerEvent().changes.fastAll { it.id == down.id && !it.pressed && androidx.compose.ui.geometry.Offset.Zero == it.position - down.position }) {
                onClick()
            }
        }
    }
}

internal fun share(context: Context, image: Image) {
    val send = Intent().apply {
        action = Intent.ACTION_SEND
        type = "image/jpeg"
        putExtra(Intent.EXTRA_STREAM, Uri.parse(image.path))
    }
    val chooser = Intent.createChooser(send, null)
    context.startActivity(chooser)
}
