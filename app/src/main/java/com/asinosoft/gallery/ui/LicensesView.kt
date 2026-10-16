package com.asinosoft.gallery.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R

private data class LicenseEntry(
    val name: String,
    val license: String,
    val url: String
)

private val LICENSES = listOf(
    LicenseEntry(
        "AndroidX / Jetpack",
        "Apache License 2.0",
        "https://developer.android.com/jetpack"
    ),
    LicenseEntry(
        "Jetpack Compose",
        "Apache License 2.0",
        "https://developer.android.com/jetpack/compose"
    ),
    LicenseEntry("Kotlin", "Apache License 2.0", "https://kotlinlang.org"),
    LicenseEntry("Coil", "Apache License 2.0", "https://coil-kt.github.io/coil/"),
    LicenseEntry(
        "Media3 / ExoPlayer",
        "Apache License 2.0",
        "https://developer.android.com/media/media3"
    ),
    LicenseEntry(
        "Room",
        "Apache License 2.0",
        "https://developer.android.com/training/data-storage/room"
    ),
    LicenseEntry("Hilt / Dagger", "Apache License 2.0", "https://dagger.dev/hilt/"),
    LicenseEntry("OkHttp", "Apache License 2.0", "https://square.github.io/okhttp/"),
    LicenseEntry("Gson", "Apache License 2.0", "https://github.com/google/gson"),
    LicenseEntry("Accompanist", "Apache License 2.0", "https://google.github.io/accompanist/"),
    LicenseEntry(
        "Sardine Android (WebDAV)",
        "Apache License 2.0",
        "https://github.com/thegrizzlylabs/sardine-android"
    ),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LicensesView(
    onClose: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.third_party_licenses)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = stringResource(R.string.close)
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.third_party_licenses_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            LICENSES.forEachIndexed { index, entry ->
                if (index > 0) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                }
                Text(
                    text = entry.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = entry.license,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = entry.url,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
