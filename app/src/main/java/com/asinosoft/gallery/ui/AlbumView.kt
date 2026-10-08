package com.asinosoft.gallery.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.DateFilter
import com.asinosoft.gallery.model.ImageListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumView(
    onMediaClick: (Media, Set<String>, DateFilter?) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    model: ImageListViewModel = hiltViewModel()
) {
    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { paddingValues ->
        ImageListView(
            onMediaClick,
            onClose,
            contentPadding = PaddingValues(
                top = 36.dp + paddingValues.calculateTopPadding(),
                bottom = paddingValues.calculateBottomPadding()
            ),
            onBack = onClose,
            model = model
        )
    }
}
