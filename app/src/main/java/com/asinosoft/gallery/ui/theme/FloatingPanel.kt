package com.asinosoft.gallery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

@Composable
@ReadOnlyComposable
fun isDarkColorScheme(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

@Composable
@ReadOnlyComposable
fun floatingPanelColor(): Color =
    if (isDarkColorScheme()) FloatingPanelDark else MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)

@Composable
@ReadOnlyComposable
fun floatingPanelSelectedColor(): Color =
    if (isDarkColorScheme()) Color.White.copy(alpha = 0.16f) else Color.Black.copy(alpha = 0.15f)

private val FloatingPanelDark = Color(0xFF2E2E2E).copy(alpha = 0.82f)
