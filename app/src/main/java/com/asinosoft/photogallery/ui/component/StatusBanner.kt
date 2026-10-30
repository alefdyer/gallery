package com.asinosoft.photogallery.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Плашка статуса. Шкала всегда видимая и заполняется слева направо.
 * [progress] 0f..1f — доля заполнения; null — «бегущая» анимация, пока нет цифр.
 */
@Composable
fun BoxScope.StatusBanner(
    text: String?,
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    progress: Float? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    AnimatedVisibility(
        visible = !text.isNullOrBlank(),
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier
            .align(Alignment.TopCenter)
            .statusBarsPadding()
            .padding(top = 120.dp),
    ) {
        val bg = if (dark) {
            Color.Black.copy(alpha = 0.88f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)
        }
        val fg = if (dark) Color.White else MaterialTheme.colorScheme.onSurface
        val track = if (dark) {
            Color.White.copy(alpha = 0.22f)
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f)
        }
        val bar = if (dark) Color(0xFF7CDBFF) else MaterialTheme.colorScheme.primary
        val clamped = progress?.takeIf { it.isFinite() }?.coerceIn(0f, 1f)
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = bg,
            tonalElevation = if (dark) 0.dp else 6.dp,
            shadowElevation = 8.dp,
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .fillMaxWidth(0.9f),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = text.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = fg,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = if (clamped != null) {
                            "${(clamped * 100f).roundToInt()}%"
                        } else {
                            "…"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = bar,
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                FillProgressBar(
                    progress = clamped,
                    barColor = bar,
                    trackColor = track,
                )
                if (!actionLabel.isNullOrBlank() && onAction != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    TextButton(
                        onClick = onAction,
                        modifier = Modifier.align(Alignment.CenterHorizontally),
                    ) {
                        Text(
                            text = actionLabel,
                            fontWeight = FontWeight.SemiBold,
                            color = bar,
                        )
                    }
                }
            }
        }
    }
}

/** Явная полоса: track + заполнение слева (не indeterminate-«бегунок» Material). */
@Composable
private fun FillProgressBar(
    progress: Float?,
    barColor: Color,
    trackColor: Color,
) {
    val fill = if (progress != null) {
        progress.coerceIn(0f, 1f)
    } else {
        val transition = rememberInfiniteTransition(label = "bannerProgress")
        val pulse by transition.animateFloat(
            initialValue = 0.08f,
            targetValue = 0.55f,
            animationSpec = infiniteRepeatable(
                animation = tween(900, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse,
            ),
            label = "bannerPulse",
        )
        pulse
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(10.dp)
            .clip(RoundedCornerShape(5.dp))
            .background(trackColor),
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(fraction = fill.coerceIn(0.02f, 1f))
                .background(barColor),
        )
    }
}
