package com.asinosoft.gallery.ui.component

import android.icu.text.DateFormatSymbols
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.DateFilter

import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateMap

private val monthNames = DateFormatSymbols().months

@Composable
fun DateFolderTreeView(
    images: List<Media>,
    expandedNodes: SnapshotStateMap<String, Boolean>,
    onSelectDateFilter: (DateFilter) -> Unit,
    modifier: Modifier = Modifier,
    initialScrollIndex: Int = 0,
    initialScrollOffset: Int = 0,
    onUpdateScrollPosition: (Int, Int) -> Unit = { _, _ -> },
    contentPadding: PaddingValues = PaddingValues(0.dp)
) {
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = initialScrollIndex,
        initialFirstVisibleItemScrollOffset = initialScrollOffset
    )

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect { (index, offset) ->
                onUpdateScrollPosition(index, offset)
            }
    }

    // Группировка: Year -> Month -> Day -> List<Media>
    val yearGroups = remember(images) {
        images.groupBy { it.date.year }
            .mapValues { (_, yearImages) ->
                yearImages.groupBy { it.date.monthValue }
                    .mapValues { (_, monthImages) ->
                        monthImages.groupBy { it.date.dayOfMonth }
                    }
            }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 28.dp, bottom = 24.dp)
            ) {
                yearGroups.forEach { (year, monthGroups) ->
                    val yearKey = "Y_$year"
                    val isYearExpanded = expandedNodes[yearKey] ?: false
                    val yearTotalCount = monthGroups.values.flatMap { it.values }.sumOf { it.size }

                    item(key = yearKey) {
                        FolderTreeItem(
                            title = "$year год",
                            itemCount = yearTotalCount,
                            level = 0,
                            isExpanded = isYearExpanded,
                            onToggleExpand = { expandedNodes[yearKey] = !isYearExpanded },
                            onClick = { onSelectDateFilter(DateFilter(year = year)) }
                        )
                    }

                    if (isYearExpanded) {
                        monthGroups.forEach { (month, dayGroups) ->
                            val monthKey = "M_${year}_$month"
                            val isMonthExpanded = expandedNodes[monthKey] ?: false
                            val monthTotalCount = dayGroups.values.sumOf { it.size }
                            val monthName = monthNames.getOrNull(month - 1) ?: "$month"

                            item(key = monthKey) {
                                FolderTreeItem(
                                    title = monthName,
                                    itemCount = monthTotalCount,
                                    level = 1,
                                    isExpanded = isMonthExpanded,
                                    onToggleExpand = { expandedNodes[monthKey] = !isMonthExpanded },
                                    onClick = { onSelectDateFilter(DateFilter(year = year, month = month)) }
                                )
                            }

                            if (isMonthExpanded) {
                                dayGroups.forEach { (day, dayImages) ->
                                    val dayKey = "D_${year}_${month}_$day"

                                    item(key = dayKey) {
                                        FolderTreeItem(
                                            title = "$day число",
                                            itemCount = dayImages.size,
                                            level = 2,
                                            isExpanded = false,
                                            hasChildren = false,
                                            onToggleExpand = {},
                                            onClick = { onSelectDateFilter(DateFilter(year = year, month = month, day = day)) }
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
private fun FolderTreeItem(
    title: String,
    itemCount: Int,
    level: Int,
    isExpanded: Boolean,
    hasChildren: Boolean = true,
    onToggleExpand: () -> Unit,
    onClick: () -> Unit
) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (level * 24).dp)
                .clickable { onClick() }
                .padding(vertical = 10.dp, horizontal = 8.dp)
        ) {
            if (hasChildren) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Color.Transparent,
                    modifier = Modifier
                        .size(28.dp)
                        .clickable { onToggleExpand() }
                ) {
                    Icon(
                        painter = painterResource(
                            if (isExpanded) R.drawable.expand_more else R.drawable.chevron_right
                        ),
                        contentDescription = if (isExpanded) "Свернуть" else "Развернуть",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(20.dp)
                    )
                }
            } else {
                Spacer(modifier = Modifier.width(28.dp))
            }

            Spacer(modifier = Modifier.width(4.dp))

            Icon(
                painter = painterResource(R.drawable.folder_yellow),
                contentDescription = null,
                tint = Color.Unspecified,
                modifier = Modifier.size(24.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = "$itemCount",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}
