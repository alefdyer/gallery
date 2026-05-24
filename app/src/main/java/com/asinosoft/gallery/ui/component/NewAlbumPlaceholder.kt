package com.asinosoft.gallery.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R

@Composable
fun NewAlbumPlaceholder(
    modifier: Modifier = Modifier
) {
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .padding(1.dp)
                .clip(RoundedCornerShape(12.dp))
    ) {
        Icon(
            painter = painterResource(R.drawable.add),
            contentDescription = "Add",
            modifier =
                Modifier
                    .align(Alignment.Center)
                    .size(64.dp),
            tint = Color.White
        )
    }
}
