package com.asinosoft.gallery.ui

import android.content.ClipData
import android.content.ClipDescription
import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.draganddrop.dragAndDropSource
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragAndDropTransferData
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.CategoryWithAlbums
import com.asinosoft.gallery.data.name
import com.asinosoft.gallery.model.AlbumsViewModel
import com.asinosoft.gallery.ui.component.AlbumCover
import com.asinosoft.gallery.ui.component.NewAlbumCategoryDialog
import com.google.gson.Gson
import kotlinx.coroutines.launch

@Composable
fun AlbumListView(
    modifier: Modifier = Modifier,
    onAlbumClick: (Album) -> Unit = {},
    nestedScroll: NestedScrollConnection,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    model: AlbumsViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val categories by model.albums.collectAsState(initial = listOf())
    val lazyListState = rememberLazyListState()
    var droppedAlbum by remember { mutableStateOf<Album?>(null) }
    var showNewCategoryDialog by remember { mutableStateOf(false) }
    var isDragActive by remember { mutableStateOf(false) }
    var dropCategory by remember { mutableStateOf<CategoryWithAlbums?>(null) }

    val dragAndDropTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                super.onStarted(event)

                isDragActive = true
                val index = lazyListState.getIndexAt(event)
                dropCategory = index?.let(categories::getOrNull)
            }

            override fun onMoved(event: DragAndDropEvent) {
                super.onMoved(event)

                val index = lazyListState.getIndexAt(event)
                dropCategory = index?.let(categories::getOrNull)

                val y = event.toAndroidDragEvent().y
                scope.launch {
                    val threshold = lazyListState.layoutInfo.viewportSize.height / 8f

                    if (y > lazyListState.layoutInfo.viewportEndOffset - threshold) {
                        lazyListState.scrollBy(threshold)
                    } else if (y < lazyListState.layoutInfo.viewportStartOffset + threshold) {
                        lazyListState.scrollBy(-threshold)
                    }
                }
            }

            override fun onEnded(event: DragAndDropEvent) {
                super.onEnded(event)

                isDragActive = false
                dropCategory = null
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                event.asAlbum()?.let { album ->
                    val category =
                        lazyListState.getIndexAt(event)?.let {
                            categories.getOrNull(it)
                        }

                    if (category == null) {
                        droppedAlbum = album
                        showNewCategoryDialog = true
                    } else {
                        model.moveAlbumIntoCategory(album, category.category)
                    }
                }

                return true
            }
        }
    }

    LazyColumn(
        state = lazyListState,
        contentPadding = contentPadding,
        modifier = modifier
            .fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = {
                    it.mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_INTENT)
                },
                target = dragAndDropTarget
            )
            .nestedScroll(nestedScroll)
    ) {
        items(categories) { category ->
            AlbumCategoriesRow(
                category,
                onAlbumClick,
                if (category == dropCategory) Modifier.border(1.dp, Color.Red)
                else Modifier
            )
        }

        if (isDragActive) {
            item {
                NewAlbumCategory(
                    if (dropCategory == null) Modifier.border(1.dp, Color.Red)
                    else Modifier
                )
            }
        }
    }

    if (showNewCategoryDialog) {
        NewAlbumCategoryDialog(
            onCreateCategory = { categoryName ->
                droppedAlbum?.let { album ->
                    model.moveAlbumIntoNewCategory(album, categoryName)
                }
            },
            onDismiss = {
                showNewCategoryDialog = false
            }
        )
    }
}

@Composable
private fun AlbumCategoriesRow(
    category: CategoryWithAlbums,
    onAlbumClick: (Album) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = category.category.name(),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 8.dp)
        )

        LazyRow {
            items(category.albums, { it.album.id }) { album ->
                AlbumCover(
                    album,
                    Modifier
                        .size(LocalWindowInfo.current.containerDpSize.width / 3)
                        .dragAndDropSource { _ -> album.album.toDragAndDrop() }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                awaitFirstDown()
                                withTimeoutOrNull(300) { waitForUpOrCancellation() }?.let {
                                    onAlbumClick(album.album)
                                }
                            }
                        }
                )
            }
        }
    }
}

@Composable
fun NewAlbumCategory(
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Text(
            text = stringResource(R.string.new_category),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(start = 8.dp)
        )

        Image(
            painterResource(R.drawable.add),
            null,
            Modifier.size(LocalWindowInfo.current.containerDpSize.width / 3)
        )
    }
}

private fun Album.toDragAndDrop() =
    DragAndDropTransferData(
        clipData = ClipData.newIntent(
            "album.to.category",
            Intent("categorize.album").apply {
                putExtra("album", Gson().toJson(this@toDragAndDrop))
            }
        )
    )

private fun DragAndDropEvent.asAlbum(): Album? =
    if (mimeTypes().contains(ClipDescription.MIMETYPE_TEXT_INTENT) && null != toAndroidDragEvent().clipData) {
        (0 until toAndroidDragEvent().clipData.itemCount)
            .map { toAndroidDragEvent().clipData.getItemAt(it) }
            .firstNotNullOfOrNull {
                Gson().fromJson(it.intent.getStringExtra("album"), Album::class.java)
            }
    } else {
        null
    }

private fun LazyListState.getIndexAt(event: DragAndDropEvent): Int? {
    val y = event.toAndroidDragEvent().y

    return layoutInfo.visibleItemsInfo.firstOrNull { item ->
        y >= item.offset && y <= item.offset + item.size
    }?.index
}
