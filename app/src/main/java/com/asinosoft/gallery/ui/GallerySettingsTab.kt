package com.asinosoft.gallery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.ui.component.GallerySettings
import com.asinosoft.gallery.ui.component.SettingsGroup
import com.asinosoft.gallery.ui.component.SettingsGroupDivider
import com.asinosoft.gallery.ui.component.SettingsPage
import com.asinosoft.gallery.ui.component.SettingsSectionTitle
import com.asinosoft.gallery.ui.component.rememberAutoplayVideo
import com.asinosoft.gallery.ui.component.rememberVideoSound

@Composable
fun GallerySettingsTab(
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val autoplay = rememberAutoplayVideo()
    val sound = rememberVideoSound()

    SettingsPage(modifier) {
        SettingsSectionTitle(stringResource(R.string.viewing_section))
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(R.string.autoplay_video),
                subtitle = stringResource(R.string.autoplay_video_hint),
                checked = autoplay,
                onCheckedChange = { GallerySettings.setAutoplayVideo(context, it) }
            )
            SettingsGroupDivider()
            SettingsSwitchRow(
                title = stringResource(R.string.video_sound),
                subtitle = stringResource(R.string.video_sound_hint),
                checked = sound,
                onCheckedChange = { GallerySettings.setVideoSound(context, it) }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
