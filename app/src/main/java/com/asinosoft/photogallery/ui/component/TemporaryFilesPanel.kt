package com.asinosoft.photogallery.ui.component

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.data.AppCacheCleanup
import com.asinosoft.photogallery.data.AppCacheEntry
import com.asinosoft.photogallery.data.AppCacheKind
import kotlinx.coroutines.launch

@Composable
fun TemporaryFilesPanel(
    modifier: Modifier = Modifier,
    sectionTitle: String? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<AppCacheEntry>>(emptyList()) }
    var clearing by remember { mutableStateOf(false) }
    val title = sectionTitle ?: stringResource(R.string.temporary_files)
    val hasAnything = entries.any { it.bytes > 0L }

    suspend fun refresh() {
        entries = AppCacheCleanup.snapshot(context)
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(R.string.cache_clear_all),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (hasAnything && !clearing) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                },
                modifier = Modifier.clickable(enabled = hasAnything && !clearing) {
                    scope.launch {
                        clearing = true
                        try {
                            AppCacheCleanup.clearAll(context)
                            refresh()
                            Toast.makeText(
                                context,
                                context.getString(R.string.cache_cleared_all),
                                Toast.LENGTH_SHORT,
                            ).show()
                        } finally {
                            clearing = false
                        }
                    }
                },
            )
        }
        SettingsGroup {
            entries.forEachIndexed { index, entry ->
                if (index > 0) SettingsGroupDivider()
                val itemTitle = stringResource(titleRes(entry.kind))
                val enabled = entry.bytes > 0L && !clearing
                Column(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) {
                            scope.launch {
                                clearing = true
                                try {
                                    AppCacheCleanup.clear(context, entry.kind)
                                    refresh()
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.temporary_files_cleared, itemTitle),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } finally {
                                    clearing = false
                                }
                            }
                        }
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                ) {
                    Text(
                        text = itemTitle,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium,
                        color = if (enabled) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                        },
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = entry.formattedSize(context),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(
                            alpha = if (enabled) 1f else 0.5f,
                        ),
                    )
                }
            }
        }
    }
}

private fun titleRes(kind: AppCacheKind): Int = when (kind) {
    AppCacheKind.THUMBNAILS -> R.string.cache_thumbnails
    AppCacheKind.IMAGE_CACHE -> R.string.cache_image
    AppCacheKind.VLC_MEDIA -> R.string.cache_video
    AppCacheKind.SHARE -> R.string.cache_share
    AppCacheKind.OTHER -> R.string.cache_other
}
