package com.asinosoft.photogallery.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.editor.StickerCategory
import kotlin.math.roundToInt

@Composable
fun StickerOrderScreen(
    onClose: () -> Unit,
    model: EditorViewModel = hiltViewModel(),
) {
    val cats by model.stickerCategories.collectAsState()
    var order by remember(cats) { mutableStateOf(cats.map { it.id }) }
    val byId = remember(cats) { cats.associateBy { it.id } }
    val listState = rememberLazyListState()
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragDelta by remember { mutableFloatStateOf(0f) }

    Column(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .statusBarsPadding()
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                model.setStickerCategoryOrder(order)
                onClose()
            }) {
                Icon(
                    painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back),
                    tint = Color.White,
                )
            }
            Text(
                text = stringResource(R.string.editor_sticker_order_title),
                color = Color.White,
                modifier = Modifier.padding(start = 8.dp),
            )
        }

        Box(
            Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .background(Color.White, RoundedCornerShape(16.dp))
        ) {
            LazyColumn(state = listState) {
                itemsIndexed(order, key = { _, id -> id }) { index, id ->
                    val cat = byId[id] ?: return@itemsIndexed
                    val isDragged = id == draggingId
                    StickerOrderRow(
                        category = cat,
                        modifier = Modifier
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                if (isDragged) {
                                    translationY = dragDelta
                                    shadowElevation = 8.dp.toPx()
                                }
                            }
                            .pointerInput(id, order) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        draggingId = id
                                        dragDelta = 0f
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragDelta += amount.y
                                        val rowH = 56.dp.toPx()
                                        val shift = (dragDelta / rowH).roundToInt()
                                        if (shift != 0) {
                                            val from = order.indexOf(id)
                                            val to = (from + shift).coerceIn(0, order.lastIndex)
                                            if (to != from) {
                                                order = order.toMutableList().also {
                                                    val item = it.removeAt(from)
                                                    it.add(to, item)
                                                }
                                                dragDelta -= shift * rowH
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggingId = null
                                        dragDelta = 0f
                                        model.setStickerCategoryOrder(order)
                                    },
                                    onDragCancel = {
                                        draggingId = null
                                        dragDelta = 0f
                                    },
                                )
                            },
                    )
                    if (index < order.lastIndex) {
                        HorizontalDivider(color = Color(0xFFE0E0E0))
                    }
                }
            }
        }
    }
}

@Composable
private fun StickerOrderRow(
    category: StickerCategory,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = category.iconEmoji, modifier = Modifier.padding(end = 12.dp))
        Text(
            text = category.defaultTitle,
            color = Color.Black,
            modifier = Modifier.weight(1f),
        )
        Icon(
            painter = painterResource(R.drawable.height),
            contentDescription = null,
            tint = Color.Gray,
        )
    }
}
