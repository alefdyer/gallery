package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class DragSelectionTest {
    @Test
    fun adjustedOffsetSubtractsContentPadding() {
        val padding = PaddingValues(start = 16.dp, top = 24.dp)
        val offset = Offset(40f, 50f)

        val adjusted = offsetForContentPadding(offset, padding, LayoutDirection.Ltr, Density(1f))

        assertEquals(24f, adjusted.x, 0.001f)
        assertEquals(26f, adjusted.y, 0.001f)
    }
}
