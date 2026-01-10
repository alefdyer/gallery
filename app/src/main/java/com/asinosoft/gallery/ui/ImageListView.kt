package com.asinosoft.gallery.ui

import android.app.Activity
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.GalleryApp
import com.asinosoft.gallery.data.HeaderItem
import com.asinosoft.gallery.data.Image
import com.asinosoft.gallery.data.ImageItem
import com.asinosoft.gallery.model.GalleryViewModel
import com.asinosoft.gallery.ui.component.GroupHeader
import com.asinosoft.gallery.ui.component.GroupItem
import com.asinosoft.gallery.ui.component.SelectionInfoBar
import com.asinosoft.gallery.util.groupByMonth

@Composable
fun ImageListView(
    images: List<Image>,
    model: GalleryViewModel = hiltViewModel(),
    onImageClick: (Image) -> Unit,
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    val items by remember(images) { mutableStateOf(groupByMonth(images)) }
    var selectedImages by remember { mutableStateOf<Set<Image>>(setOf()) }
    val selectionMode by remember { derivedStateOf { selectedImages.isNotEmpty() } }
    var selectionBarHeight by remember { mutableIntStateOf(0) }
    var imageListTopPadding by remember { mutableIntStateOf(0) }
    var closeAfterDelete by remember(images) { mutableStateOf(false) }
    val lazyGridState = rememberLazyGridState()

    fun Set<Image>.share() {
        Log.d(GalleryApp.TAG, "share ${count()} images")
        val paths: ArrayList<Uri> =
            selectedImages.map { it.path.toUri() }.toCollection(ArrayList())
        val send = Intent().apply {
            action = Intent.ACTION_SEND_MULTIPLE
            type = "image/jpeg"

            putParcelableArrayListExtra(Intent.EXTRA_STREAM, paths)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        val chooser = Intent.createChooser(send, null)
        context.startActivity(chooser)
        selectedImages = setOf()
    }


    val deleter =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            Log.d(GalleryApp.TAG, "Test: ${it.data?.getBooleanExtra("test", false)}")
            if (Activity.RESULT_OK == it.resultCode) {
                model.deleteAll(selectedImages)
                selectedImages = setOf()

                if (closeAfterDelete) {
                    onClose()
                }
            }
        }

    fun Set<Image>.delete() {
        Log.d(GalleryApp.TAG, "delete ${count()} images")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val delete: PendingIntent = MediaStore.createDeleteRequest(
                context.contentResolver,
                selectedImages.map { it.path.toUri() }
            )

            closeAfterDelete = selectedImages.count() == images.count()
            deleter.launch(IntentSenderRequest.Builder(delete.intentSender).build())
        }
    }

    LaunchedEffect(selectionMode) {
        imageListTopPadding = if (selectionMode) selectionBarHeight else 0

        val offset = if (selectionMode) selectionBarHeight else -selectionBarHeight
        lazyGridState.dispatchRawDelta(offset.toFloat())
    }

    Box {
        LazyVerticalGrid(
            state = lazyGridState,
            columns = GridCells.Fixed(3),
            modifier = Modifier.padding(top = imageListTopPadding.pxToDp())
        ) {
            items(
                items,
                span = {
                    when (it) {
                        is HeaderItem -> GridItemSpan(maxLineSpan)
                        else -> GridItemSpan(1)
                    }
                }
            ) {
                when (it) {
                    is HeaderItem -> GroupHeader(it)
                    is ImageItem -> GroupItem(
                        image = it.image,
                        selectionMode = selectionMode,
                        selectedImages = selectedImages,
                        onImageClick = onImageClick,
                        onImageSelect = { image ->
                            if (selectedImages.contains(image)) {
                                Log.d(GalleryApp.TAG, "deselect image ${image.path}")
                                selectedImages -= image
                            } else {
                                Log.d(GalleryApp.TAG, "select image ${image.path}")
                                selectedImages += image
                            }
                        }
                    )
                }
            }
        }

        AnimatedVisibility(visible = selectionMode) {
            SelectionInfoBar(
                selectedImages = selectedImages,
                modifier = Modifier.onSizeChanged { selectionBarHeight = it.height },
                onBack = {
                    selectedImages = setOf()
                },
                onShare = { it.share() },
                onDelete = { it.delete() },
            )
        }
    }
}

@Composable
fun Int.pxToDp() = with(LocalDensity.current) { this@pxToDp.toDp() }

