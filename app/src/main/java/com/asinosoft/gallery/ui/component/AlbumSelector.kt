package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumCategory
import com.asinosoft.gallery.data.name
import com.asinosoft.gallery.model.AlbumsViewModel

@Composable
fun AlbumSelector(
    modifier: Modifier = Modifier,
    onAlbumClick: (Album) -> Unit,
    onNewAlbumClick: (AlbumCategory) -> Unit,
    onNewCategoryClick: () -> Unit,
    model: AlbumsViewModel = hiltViewModel()
) {
    val categories by model.albums.collectAsState(initial = listOf())

    BoxWithConstraints(modifier) {
        val size = (maxWidth - CARD_SPACING * 2) / 3.3f

        Column(Modifier.verticalScroll(rememberScrollState())) {
            categories.forEach { category ->
                SelectorTitle(category.category.name(), category.albums.size)

                LazyRow(horizontalArrangement = Arrangement.spacedBy(CARD_SPACING)) {
                    items(category.albums, key = { it.album.id }) { album ->
                        AlbumCover(
                            album,
                            Modifier
                                .size(size)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onAlbumClick(album.album) }
                        )
                    }

                    item {
                        NewAlbumPlaceholder(
                            Modifier
                                .size(size)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onNewAlbumClick(category.category) }
                        )
                    }
                }
            }

            SelectorTitle(stringResource(R.string.new_category))

            NewAlbumPlaceholder(
                Modifier
                    .padding(bottom = 8.dp)
                    .size(size)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onNewCategoryClick() }
            )
        }
    }
}

@Composable
private fun SelectorTitle(title: String, count: Int? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(top = 12.dp, bottom = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (null != count) {
            Text(
                text = " ($count)",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private val CARD_SPACING = 8.dp
