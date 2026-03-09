package com.asinosoft.gallery.ui

import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.asinosoft.gallery.R
import com.asinosoft.gallery.data.Media
import com.asinosoft.gallery.ui.theme.Typography
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaInfoSheet(
    media: Media,
    onDismissRequest: () -> Unit,
) {
    val size = Formatter.formatShortFileSize(LocalContext.current, media.size)
    val date = media.date.format(DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM))
    val time = media.time.format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT))

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
    ) {
        Text(
            text = stringResource(R.string.info),
            style = Typography.headlineLarge,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center,
        )

        ListItem(
            leadingContent = { Icon(Icons.Default.Folder, null) },
            headlineContent = { Text(stringResource(R.string.path)) },
            supportingContent = { Text(media.filename) },
        )
        media.image?.let {
            ListItem(
                leadingContent = { Icon(Icons.Default.AspectRatio, null) },
                headlineContent = { Text(stringResource(R.string.size)) },
                supportingContent = { Text("${it.width}×${it.height}, $size") },
            )
        }
        ListItem(
            leadingContent = { Icon(Icons.Default.CalendarToday, null) },
            headlineContent = { Text(stringResource(R.string.date)) },
            supportingContent = {
                Text("$date $time")
            },
        )
    }
}
