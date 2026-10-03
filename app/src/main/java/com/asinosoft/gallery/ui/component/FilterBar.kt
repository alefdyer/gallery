package com.asinosoft.gallery.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.R
import com.asinosoft.gallery.model.ImageListViewModel

@Composable
fun FilterBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onMeasuredHeight: (Float) -> Unit = {},
    model: ImageListViewModel = hiltViewModel()
) {
    val lazyListState = rememberLazyListState()
    var showDateFilterDialog by remember { mutableStateOf(false) }
    val dateFilter by model.activeDateFilter.collectAsState()
    val filters by model.filters.collectAsState(listOf())

    LaunchedEffect(filters) {
        if (filters.isNotEmpty()) {
            lazyListState.animateScrollToItem(0)
        }
    }

    if (showDateFilterDialog) {
        val yearGroups by model.dateGroups.collectAsState(mapOf())
        DateFilterDialog(
            onClose = { showDateFilterDialog = false },
            yearGroups = yearGroups,
            selectedDate = dateFilter,
            onSelectDateFilter = { date ->
                showDateFilterDialog = false
                model.setDateFilter(date)
            },
            onClearDateFilter = {
                showDateFilterDialog = false
                model.clearDateFilter()
            }
        )
    }

    AnimatedVisibility(
        visible = visible,
        modifier = modifier.onGloballyPositioned {
            onMeasuredHeight(it.size.height.toFloat())
        }
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 4.dp,
            ) {
                Row {
                    IconButton(
                        onClick = { showDateFilterDialog = true },
                    ) {
                        Icon(
                            painter = painterResource(
                                if (dateFilter != null) R.drawable.calendar_month
                                else R.drawable.calendar_today
                            ),
                            contentDescription = "Дата",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    if (dateFilter != null) {
                        IconButton(model::clearDateFilter) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.close)
                            )
                        }
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                tonalElevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (filters.isNotEmpty()) {
                        LazyRow(
                            state = lazyListState,
                            modifier = Modifier.widthIn(max = 285.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            items(filters, key = { it.application.pkg }) { filter ->
                                filter.application.icon?.let { icon ->
                                    Image(
                                        bitmap = icon.toBitmap().asImageBitmap(),
                                        contentDescription = filter.application.name,
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .size(32.dp)
                                            .alpha(if (filter.enabled) 1f else 0.3f)
                                            .clickable { model.toggleFilter(filter) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
