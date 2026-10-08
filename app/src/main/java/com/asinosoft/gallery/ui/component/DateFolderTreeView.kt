package com.asinosoft.gallery.ui.component

import android.icu.text.DateFormatSymbols
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.asinosoft.gallery.R
import com.asinosoft.gallery.model.DateFilter
import com.asinosoft.gallery.ui.theme.Golden

private val monthNames = DateFormatSymbols().getMonths(
    DateFormatSymbols.STANDALONE,
    DateFormatSymbols.WIDE
)

private val formatMonthNames = DateFormatSymbols().getMonths(
    DateFormatSymbols.FORMAT,
    DateFormatSymbols.WIDE
)

@Composable
fun DateFilterDialog(
    onClose: () -> Unit,
    yearGroups: Map<Int, Map<Int, Map<Int, Int>>>,
    selectedDate: DateFilter?,
    onSelectDateFilter: (DateFilter) -> Unit,
    onClearDateFilter: () -> Unit
) {
    val expandedNodes = remember { mutableStateSetOf<String>() }
    val listState = rememberLazyListState()

    LaunchedEffect(selectedDate, yearGroups) {
        if (selectedDate == null || yearGroups.isEmpty()) return@LaunchedEffect

        selectedDate.month?.let { expandedNodes.add("Y_${selectedDate.year}") }
        selectedDate.day?.let { expandedNodes.add("M_${selectedDate.year}_${selectedDate.month}") }

        var selectedIndex = 0
        selectedDate.year?.let { year ->
            selectedIndex += yearGroups.count { it.key >= year }
            selectedDate.month?.let { month ->
                yearGroups.get(year)?.let { monthGroups ->
                    selectedIndex += monthGroups.count { it.key >= month }

                    selectedDate.day?.let { day ->
                        monthGroups.get(month)?.let { dayGroups ->
                            selectedIndex += dayGroups.count { it.key >= day }
                        }
                    }
                }
            }
        }

        listState.scrollToItem(selectedIndex)
    }

    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val systemBars = WindowInsets.systemBars.asPaddingValues()

        Surface(Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        top = systemBars.calculateTopPadding() + HEADER_HEIGHT + 16.dp,
                        bottom = systemBars.calculateBottomPadding() + 8.dp
                    ),
                    modifier = Modifier.fillMaxSize()
                ) {
                    yearGroups.forEach { (year, monthGroups) ->
                        val yearKey = "Y_$year"
                        val yearTotalCount = monthGroups.values.flatMap { it.values }.sum()

                        item(key = yearKey) {
                            FolderTreeItem(
                                title = "$year год",
                                itemCount = yearTotalCount,
                                level = 0,
                                isExpanded = year == selectedDate?.year,
                                isSelected = DateFilter(year) == selectedDate,
                                onToggleExpand = {
                                    if (expandedNodes.contains(yearKey))
                                        expandedNodes.remove(yearKey)
                                    else
                                        expandedNodes.add(yearKey)
                                },
                                onClick = { onSelectDateFilter(DateFilter(year = year)) }
                            )
                        }

                        if (expandedNodes.contains(yearKey)) {
                            monthGroups.forEach { (month, dayGroups) ->
                                val monthKey = "M_${year}_$month"
                                val monthTotalCount = dayGroups.values.sum()
                                val monthName = monthNames.getOrNull(month - 1) ?: "$month"

                                item(key = monthKey) {
                                    FolderTreeItem(
                                        title = monthName,
                                        itemCount = monthTotalCount,
                                        level = 1,
                                        isExpanded = expandedNodes.contains(monthKey),
                                        isSelected = DateFilter(year, month) == selectedDate,
                                        onToggleExpand = {
                                            if (expandedNodes.contains(monthKey))
                                                expandedNodes.remove(monthKey)
                                            else
                                                expandedNodes.add(monthKey)
                                        },
                                        onClick = {
                                            onSelectDateFilter(
                                                DateFilter(year, month)
                                            )
                                        }
                                    )
                                }

                                if (expandedNodes.contains(monthKey)) {
                                    dayGroups.forEach { (day, count) ->
                                        val dayKey = "D_${year}_${month}_$day"
                                        val monthName = formatMonthNames.getOrNull(month - 1)

                                        item(key = dayKey) {
                                            FolderTreeItem(
                                                title = "$day $monthName",
                                                itemCount = count,
                                                level = 2,
                                                isExpanded = false,
                                                isSelected = DateFilter(
                                                    year,
                                                    month,
                                                    day
                                                ) == selectedDate,
                                                hasChildren = false,
                                                onToggleExpand = {},
                                                onClick = {
                                                    onSelectDateFilter(
                                                        DateFilter(year, month, day)
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = systemBars.calculateTopPadding())
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                        tonalElevation = 4.dp,
                        shadowElevation = 2.dp
                    ) {
                        IconButton(onClose) {
                            Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.back))
                        }
                    }

                    if (selectedDate != null) {
                        Surface(
                            onClick = onClearDateFilter,
                            shape = RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            tonalElevation = 4.dp,
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier
                                    .height(HEADER_HEIGHT)
                                    .padding(start = 14.dp, end = 18.dp)
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp)
                                )
                                Text(
                                    text = stringResource(R.string.clear),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private val HEADER_HEIGHT = 48.dp

@Composable
private fun FolderTreeItem(
    title: String,
    itemCount: Int,
    level: Int,
    isExpanded: Boolean,
    isSelected: Boolean = false,
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
                painter = painterResource(if (isSelected) R.drawable.folder_check else R.drawable.folder),
                contentDescription = null,
                tint = if (isSelected) MaterialTheme.colorScheme.primary else Golden,
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
