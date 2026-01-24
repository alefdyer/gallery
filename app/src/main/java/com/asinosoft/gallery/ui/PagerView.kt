package com.asinosoft.gallery.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.ui.util.fastAll
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.GalleryApp
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.model.GalleryViewModel

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
    var closeAfterDelete by remember(images) { mutableStateOf(false) }

    val launcher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (Activity.RESULT_OK == it.resultCode) {
                Log.d(GalleryApp.TAG, "delete: $currentImage")
                model.deleteAll(listOf(currentImage))

                if (closeAfterDelete) {
                    onClose()
                }
            }
        }

    fun Image.share() {
        Log.d(GalleryApp.TAG, "Share $path")
        val send =
            Intent().apply {
                action = Intent.ACTION_SEND
                type = "image/jpeg"
                putExtra(Intent.EXTRA_STREAM, path.toUri())
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        val chooser = Intent.createChooser(send, null)
        context.startActivity(chooser)
    }

    fun Image.edit() {
        Log.d(GalleryApp.TAG, "Edit $path")
        val edit =
            Intent().apply {
                action = Intent.ACTION_EDIT
                data = path.toUri()
            }
        context.startActivity(edit)
    }

    fun Image.delete() {
        Log.d(GalleryApp.TAG, "Delete $path")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val delete: PendingIntent =
                MediaStore.createDeleteRequest(
                    context.contentResolver,
                    listOf(path.toUri()),
                )

            closeAfterDelete = 1 == images.count()
            launcher.launch(IntentSenderRequest.Builder(delete.intentSender).build())
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
                            if (amount > 0 && !showImageInfo) onClose()
                            if (amount < 0) showImageInfo = true
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
                onShare = { currentImage.share() },
                onEdit = { currentImage.edit() },
                onSearch = {},
                onDelete = { currentImage.delete() },
            )
        }

        BackHandler(onBack = onClose)
    }
}

internal fun Modifier.onSingleClick(onClick: () -> Unit): Modifier =
    this then
        Modifier.pointerInput(Unit) {
            while (true) {
                awaitPointerEventScope {
                    val down = awaitFirstDown(false)

                    if (awaitPointerEvent().changes.fastAll {
                            it.id == down.id &&
                                !it.pressed &&
                                androidx.compose.ui.geometry.Offset.Zero == it.position - down.position
                        }
                    ) {
                        onClick()
                    }
                }
            }
        }
