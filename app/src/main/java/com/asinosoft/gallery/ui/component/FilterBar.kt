package com.asinosoft.gallery.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Filter
import com.asinosoft.gallery.model.DateFilter
import com.asinosoft.gallery.model.ImageListViewModel
import com.asinosoft.gallery.ui.theme.floatingPanelColor

@Composable
fun FilterBar(
    visible: Boolean,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onAppsLongPress: () -> Unit = {},
    onMeasuredHeight: (Float) -> Unit = {},
    model: ImageListViewModel = hiltViewModel()
) {
    val lazyListState = rememberLazyListState()
    var showDateFilterDialog by remember { mutableStateOf(false) }
    val dateListState = rememberLazyListState()
    val dateExpandedNodes = remember { mutableStateSetOf<String>() }
    var dateListPositionFor by remember { mutableStateOf<DateFilter?>(null) }
    val dateFilter by model.activeDateFilter.collectAsState()
    val filters by model.filters.collectAsState(listOf())
    val haptic = LocalHapticFeedback.current
    val openAppsMenu = {
        if (filters.isNotEmpty()) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onAppsLongPress()
        }
    }

    val filterPackages = remember(filters) { filters.map { it.application.pkg } }
    LaunchedEffect(filterPackages) {
        if (filterPackages.isNotEmpty()) {
            lazyListState.animateScrollToItem(0)
        }
    }

    if (showDateFilterDialog) {
        val yearGroups by model.dateGroups.collectAsState()
        val recentDates = remember { model.recentDateFilters() }
        DateFilterDialog(
            onClose = { showDateFilterDialog = false },
            yearGroups = yearGroups,
            selectedDate = dateFilter,
            recentDates = recentDates,
            listState = dateListState,
            expandedNodes = dateExpandedNodes,
            restorePosition = null != dateFilter && dateFilter == dateListPositionFor,
            onSelectDateFilter = { date ->
                showDateFilterDialog = false
                dateListPositionFor = date
                model.pickDateFilter(date)
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
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(50),
                color = floatingPanelColor(),
                tonalElevation = 4.dp,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (onBack != null) {
                        Box(
                            contentAlignment = Alignment.CenterEnd,
                            modifier = Modifier
                                .size(width = 40.dp, height = 48.dp)
                                .clickable(onClick = onBack)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = stringResource(R.string.back),
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    if (dateFilter == null) {
                        IconButton(
                            onClick = { showDateFilterDialog = true },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.calendar_color),
                                contentDescription = "Дата",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(32.dp)
                            )
                        }
                    } else {
                        Box(
                            contentAlignment = Alignment.CenterEnd,
                            modifier = Modifier
                                .size(width = 48.dp, height = 48.dp)
                                .clickable { showDateFilterDialog = true }
                                .padding(end = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.calendar_color_selected),
                                contentDescription = "Дата",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        Box(
                            contentAlignment = Alignment.CenterStart,
                            modifier = Modifier
                                .size(width = 36.dp, height = 48.dp)
                                .clickable(onClick = model::clearDateFilter)
                                .padding(start = 4.dp)
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.close),
                                tint = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            Box(
                contentAlignment = Alignment.CenterEnd,
                modifier = Modifier.weight(1f)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = floatingPanelColor(),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        modifier = Modifier
                            .pointerInput(Unit) { detectTapGestures(onLongPress = { openAppsMenu() }) }
                            .padding(vertical = 4.dp),
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
                                                .combinedClickable(
                                                    onClick = { model.toggleFilter(filter) },
                                                    onLongClick = { openAppsMenu() }
                                                )
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
}

@Composable
fun AppsFilterPanel(
    filters: List<Filter>,
    onToggle: (Filter, Boolean) -> Unit,
    onShowAll: () -> Unit,
    onHideAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        modifier = modifier.widthIn(min = 260.dp, max = 320.dp)
    ) {
        Column(Modifier.padding(vertical = 8.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                TextButton(
                    onClick = onHideAll,
                    enabled = filters.any { it.enabled }
                ) {
                    Text(stringResource(R.string.filter_hide_all))
                }
                TextButton(
                    onClick = onShowAll,
                    enabled = filters.any { !it.enabled }
                ) {
                    Text(stringResource(R.string.filter_show_all))
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp)
            ) {
                filters.forEach { filter ->
                    key(filter.application.pkg) {
                        AppsFilterItem(filter = filter, onToggle = onToggle)
                    }
                }
            }
        }
    }
}

@Composable
private fun AppsFilterItem(
    filter: Filter,
    onToggle: (Filter, Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(filter, !filter.enabled) }
            .padding(horizontal = 16.dp, vertical = 6.dp)
    ) {
        filter.application.icon?.let { icon ->
            Image(
                bitmap = remember(icon) { icon.toBitmap().asImageBitmap() },
                contentDescription = null,
                modifier = Modifier
                    .size(32.dp)
                    .alpha(if (filter.enabled) 1f else 0.4f)
            )
        }
        Text(
            text = filter.application.name,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = filter.enabled,
            onCheckedChange = { onToggle(filter, it) }
        )
    }
}
