package com.asinosoft.gallery.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.ui.component.SettingsGroup
import com.asinosoft.gallery.ui.component.SettingsGroupDivider
import com.asinosoft.gallery.ui.component.SettingsPage

@Composable
fun AboutAppTab(
    version: String,
    onSayThanks: () -> Unit,
    onRecommend: () -> Unit,
    onSupport: () -> Unit,
    onLicenses: () -> Unit,
    onPrivacy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SettingsPage(modifier) {
        Text(
            text = stringResource(R.string.app_version, version),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(bottom = 12.dp)
        )

        SettingsGroup {
            AboutItem(
                icon = R.drawable.thumb_up,
                title = stringResource(R.string.say_thanks),
                subtitle = stringResource(R.string.say_thanks_hint),
                onClick = onSayThanks
            )
            SettingsGroupDivider()
            AboutItem(
                icon = R.drawable.share,
                title = stringResource(R.string.recommend_friends),
                subtitle = stringResource(R.string.recommend_friends_hint),
                onClick = onRecommend
            )
            SettingsGroupDivider()
            AboutItem(
                icon = R.drawable.mail,
                title = stringResource(R.string.contact_support),
                subtitle = stringResource(R.string.contact_support_hint),
                onClick = onSupport
            )
            SettingsGroupDivider()
            AboutItem(
                icon = R.drawable.description,
                title = stringResource(R.string.third_party_licenses),
                subtitle = stringResource(R.string.third_party_licenses_hint),
                onClick = onLicenses
            )
            SettingsGroupDivider()
            AboutItem(
                icon = R.drawable.privacy_tip,
                title = stringResource(R.string.privacy_policy),
                subtitle = stringResource(R.string.privacy_policy_hint),
                onClick = onPrivacy
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun AboutItem(
    icon: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(16.dp))
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
        Icon(
            painter = painterResource(R.drawable.chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}
