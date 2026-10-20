package com.asinosoft.gallery.ui.component

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.edit
import com.asinosoft.gallery.R
import com.asinosoft.gallery.ui.theme.floatingPanelColor
import kotlinx.coroutines.delay

const val TIP_PHOTO_DATE = "tip_photo_date"
const val TIP_DAY_SWIPE = "tip_day_swipe"
const val TIP_PHOTO_COUNTERS = "tip_photo_counters"
const val TIP_PHOTO_APPS_FILTER = "tip_photo_apps_filter"
const val TIP_ALBUM_CATEGORY = "tip_album_category"
const val TIP_ALBUM_DRAG = "tip_album_drag"

fun isTipSeen(context: Context, key: String): Boolean =
    tipPrefs(context).getBoolean(key, false)

fun markTipSeen(context: Context, key: String) {
    tipPrefs(context).edit { putBoolean(key, true) }
}

private fun tipPrefs(context: Context) =
    context.applicationContext.getSharedPreferences(FEATURE_TIPS_PREFS, Context.MODE_PRIVATE)

private const val FEATURE_TIPS_PREFS = "feature_tips"

data class FeatureTipState(
    val visible: Boolean,
    val dismiss: () -> Unit
)

@Composable
fun rememberFeatureTip(
    key: String,
    enabled: Boolean = true,
    delayMs: Long = 0L
): FeatureTipState {
    val context = LocalContext.current
    var visible by remember(key) { mutableStateOf(false) }

    LaunchedEffect(key, enabled, delayMs) {
        if (!enabled || isTipSeen(context, key)) {
            visible = false
            return@LaunchedEffect
        }
        if (delayMs > 0) delay(delayMs)
        if (enabled && !isTipSeen(context, key)) {
            visible = true
        }
    }

    return FeatureTipState(
        visible = visible && enabled,
        dismiss = {
            visible = false
            markTipSeen(context, key)
        }
    )
}

@Composable
fun FeatureTipCard(
    text: String,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .widthIn(max = 260.dp)
            .zIndex(2f),
        shape = RoundedCornerShape(16.dp),
        color = floatingPanelColor(),
        tonalElevation = 6.dp,
        shadowElevation = 4.dp
    ) {
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 4.dp)
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 6.dp)
            )
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.tip_got_it))
            }
        }
    }
}
