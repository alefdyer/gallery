package com.asinosoft.gallery.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.asinosoft.gallery.R
import com.asinosoft.gallery.ui.theme.FavoriteRed
import com.asinosoft.gallery.ui.theme.floatingPanelColor

@Composable
fun PagerBottomBar(
    favorite: Boolean,
    onAddToAlbum: () -> Unit,
    onFavorite: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = floatingPanelColor(),
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onAddToAlbum) {
                Icon(
                    painter = painterResource(R.drawable.playlist_add),
                    contentDescription = stringResource(R.string.add_to_album)
                )
            }
            IconButton(onClick = onFavorite) {
                Icon(
                    painter = painterResource(if (favorite) R.drawable.favorite else R.drawable.favorite_border),
                    contentDescription = stringResource(
                        if (favorite) R.string.remove_from_favorites else R.string.add_to_favorites
                    ),
                    tint = if (favorite) FavoriteRed else LocalContentColor.current
                )
            }
            IconButton(onClick = onShare) {
                Icon(
                    painter = painterResource(R.drawable.share),
                    contentDescription = stringResource(R.string.share)
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    painter = painterResource(R.drawable.brush),
                    contentDescription = stringResource(R.string.edit)
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    painter = painterResource(R.drawable.delete),
                    contentDescription = stringResource(R.string.delete)
                )
            }
        }
    }
}
