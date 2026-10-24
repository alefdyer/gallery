package com.asinosoft.photogallery.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.asinosoft.photogallery.R
import com.asinosoft.photogallery.model.BackupJob
import com.asinosoft.photogallery.model.BackupUiState

@Composable
fun BoxScope.BackupProgressBanner(
    state: BackupUiState,
    modifier: Modifier = Modifier,
) {
    val job = state.job
    AnimatedVisibility(
        visible = null != job && state.busy,
        modifier = modifier
            .align(Alignment.BottomCenter)
            .padding(bottom = 24.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(0.9f)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = stringResource(
                        when (job) {
                            BackupJob.Saving -> R.string.backup_progress_saving
                            BackupJob.Restoring -> R.string.backup_progress_restoring
                            BackupJob.Deleting -> R.string.backup_progress_deleting
                            null -> R.string.backup_section
                        }
                    ),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                if (state.progressTotal > 0) {
                    LinearProgressIndicator(
                        progress = { state.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(
                            R.string.progress_current_of_total,
                            state.progressCurrent,
                            state.progressTotal
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp),
                    )
                }
            }
        }
    }
}
