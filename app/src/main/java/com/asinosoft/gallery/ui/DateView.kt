package com.asinosoft.gallery.ui

import android.icu.text.DateFormatSymbols
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.DateFilter
import com.asinosoft.gallery.model.ImageListViewModel

private val monthNamesGenitive = arrayOf(
    "Января", "Февраля", "Марта", "Апреля", "Мая", "Июня",
    "Июля", "Августа", "Сентября", "Октября", "Ноября", "Декабря"
)
private val monthNamesNominative = DateFormatSymbols().months

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateView(
    year: Int,
    month: Int,
    day: Int,
    onMediaClick: (Int, Int, Int, Media, Set<String>) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    model: ImageListViewModel = hiltViewModel()
) {
    val topScroll = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val density = LocalDensity.current
    val topBarPadding = 8.dp

    val filterYear = if (year > 0) year else null
    val filterMonth = if (month > 0) month else null
    val filterDay = if (day > 0) day else null

    LaunchedEffect(year, month, day) {
        if (model.activeDateFilter.value == null) {
            model.setDateFilter(DateFilter(filterYear, filterMonth, filterDay))
        }
    }

    val activeDateFilter by model.activeDateFilter.collectAsState()

    val titleText = when {
        activeDateFilter?.day != null && activeDateFilter?.month != null && activeDateFilter?.year != null -> {
            val monthName = monthNamesGenitive.getOrNull(activeDateFilter!!.month!! - 1) ?: "${activeDateFilter!!.month}"
            "${activeDateFilter!!.day} $monthName ${activeDateFilter!!.year}"
        }
        activeDateFilter?.month != null && activeDateFilter?.year != null -> {
            val monthName = monthNamesNominative.getOrNull(activeDateFilter!!.month!! - 1) ?: "${activeDateFilter!!.month}"
            "$monthName ${activeDateFilter!!.year}"
        }
        activeDateFilter?.year != null -> {
            "${activeDateFilter!!.year} год"
        }
        else -> ""
    }

    val handleClose = {
        onClose()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(topScroll.nestedScrollConnection),
    ) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(year, month, day) {
                    var dragOffset = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (dragOffset < -80f) {
                                model.getAdjacentDateFilter(1)?.let { model.setDateFilter(it) }
                            } else if (dragOffset > 80f) {
                                model.getAdjacentDateFilter(-1)?.let { model.setDateFilter(it) }
                            }
                            dragOffset = 0f
                        },
                        onDragCancel = { dragOffset = 0f },
                        onHorizontalDrag = { _, dragAmount ->
                            dragOffset += dragAmount
                        }
                    )
                }
        ) {
            ImageListView(
                onMediaClick = { media, filters ->
                    val curFilter = activeDateFilter
                    val curYear = curFilter?.year ?: year
                    val curMonth = curFilter?.month ?: month
                    val curDay = curFilter?.day ?: day
                    onMediaClick(curYear, curMonth, curDay, media, filters)
                },
                onClose = handleClose,
                scrollBehavior = topScroll,
                contentPadding = PaddingValues(
                    top = 72.dp + paddingValues.calculateTopPadding(),
                    bottom = paddingValues.calculateBottomPadding()
                )
            )

            AnimatedVisibility(
                visible = true,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(
                        top = topBarPadding + paddingValues.calculateTopPadding(),
                        start = 12.dp
                    )
                    .onGloballyPositioned {
                        topScroll.state.heightOffsetLimit =
                            -it.size.height.toFloat() - with(density) { (topBarPadding + paddingValues.calculateTopPadding()).toPx() }
                    }
                    .offset { IntOffset(0, topScroll.state.heightOffset.toInt()) }
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                    tonalElevation = 4.dp
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        IconButton(onClick = handleClose) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = "Назад"
                            )
                        }

                        Text(
                            titleText,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}
