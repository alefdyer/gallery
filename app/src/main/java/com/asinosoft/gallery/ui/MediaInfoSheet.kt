package com.asinosoft.gallery.ui

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Album
import com.asinosoft.gallery.data.AlbumWithCover
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.model.MediaInfoSheetModel
import com.asinosoft.gallery.ui.component.AlbumCover
import com.asinosoft.gallery.ui.theme.Typography
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoSheet(
    media: Media,
    onAlbumClick: (Album) -> Unit,
    onDismissRequest: () -> Unit,
    model: MediaInfoSheetModel = hiltViewModel()
) {
    val size = Formatter.formatShortFileSize(LocalContext.current, media.size)
    val date = media.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val time = media.time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    LaunchedEffect(media) {
        model.getMediaInfo(media)
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest
    ) {
        val albums: List<AlbumWithCover> by model.albums.collectAsState()
        LazyRow {
            items(items = albums, key = { it.album.id }) { album ->
                AlbumCover(
                    album,
                    Modifier
                        .size(LocalWindowInfo.current.containerDpSize.width / 3)
                        .clickable { onAlbumClick(album.album) }
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
