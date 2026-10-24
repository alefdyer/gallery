package com.asinosoft.photogallery.ui

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.Album
import com.asinosoft.photogallery.data.AlbumWithCover
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.model.PagerViewModel
import com.asinosoft.photogallery.ui.component.AddToAlbumDialog
import com.asinosoft.photogallery.ui.component.AlbumCover
import com.asinosoft.photogallery.ui.component.NewAlbumPlaceholder
import com.asinosoft.photogallery.ui.theme.Typography
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoSheet(
    media: Media,
    model: PagerViewModel,
    onAlbumClick: (Album) -> Unit,
    onDismissRequest: () -> Unit
) {
    val size = Formatter.formatShortFileSize(LocalContext.current, media.size)
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(locale)
    }
    val timeFormatter = remember(locale) {
        DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale)
    }
    val date = media.date.format(dateFormatter)
    val time = media.time.format(timeFormatter)
    val coverSize = LocalWindowInfo.current.containerDpSize.width / 3
    var showAddToAlbum by remember { mutableStateOf(false) }

    LaunchedEffect(media) {
        model.getMediaInfo(media)
    }

    if (showAddToAlbum) {
        AddToAlbumDialog(
            onPickAlbum = { model.addToAlbum(media.id, it.id) },
            onCreateAlbum = { name, category -> model.addToNewAlbum(media.id, name, category) },
            onCreateAlbumInNewCategory = { name, categoryName ->
                model.addToNewAlbumInNewCategory(media.id, name, categoryName)
            },
            onDismiss = { showAddToAlbum = false }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest
    ) {
        val albums: List<AlbumWithCover> by model.albums.collectAsState()
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 12.dp)
        ) {
            items(items = albums, key = { it.album.id }) { album ->
                Box(
                    Modifier
                        .size(coverSize)
                        .clip(RoundedCornerShape(12.dp))
                ) {
                    AlbumCover(
                        album,
                        Modifier
                            .fillMaxSize()
                            .clickable { onAlbumClick(album.album) }
                    )
                    RemoveFromAlbumButton(
                        onClick = { model.removeFromAlbum(media.id, album.album.id) },
                        modifier = Modifier.align(Alignment.TopEnd)
                    )
                }
            }
            item(key = ADD_ALBUM_KEY) {
                NewAlbumPlaceholder(
                    modifier = Modifier.size(coverSize),
                    onClick = { showAddToAlbum = true }
                )
            }
        }

        Text(
            text = stringResource(R.string.info),
            style = Typography.headlineLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        ListItem(
            leadingContent = { Icon(painterResource(R.drawable.folder), null) },
            headlineContent = { Text(stringResource(R.string.path)) },
            supportingContent = { Text(media.filename) }
        )
        media.image?.let {
            ListItem(
                leadingContent = { Icon(painterResource(R.drawable.aspect_ratio), null) },
                headlineContent = { Text(stringResource(R.string.size)) },
                supportingContent = { Text("${it.width}×${it.height}, $size") }
            )
        }
        ListItem(
            leadingContent = { Icon(painterResource(R.drawable.calendar_today), null) },
            headlineContent = { Text(stringResource(R.string.date)) },
            supportingContent = {
                Text("$date $time")
            }
        )
    }
}

@Composable
private fun RemoveFromAlbumButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .padding(6.dp)
            .size(32.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.close),
            contentDescription = stringResource(R.string.remove_from_album),
            tint = Color.White,
            modifier = Modifier.size(18.dp)
        )
    }
}

private const val ADD_ALBUM_KEY = "add"
