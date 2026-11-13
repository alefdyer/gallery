package com.asinosoft.photogallery

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import android.content.ClipData
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.asinosoft.photogallery.data.Media
import com.asinosoft.photogallery.data.MediaDao
import com.asinosoft.photogallery.data.picker.GalleryDocumentsProvider
import com.asinosoft.photogallery.ui.theme.GalleryTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Запасной путь для ACTION_GET_CONTENT / ACTION_PICK —
 * когда вызывают нас напрямую, а не через DocumentsUI.
 */
@AndroidEntryPoint
class MediaPickerActivity : ComponentActivity() {
    @Inject
    lateinit var mediaDao: MediaDao

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val mimeFilter = intent.type?.takeIf { it.isNotBlank() && it != "*/*" }
        val allowMultiple = intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false)

        setContent {
            GalleryTheme {
                var items by remember { mutableStateOf<List<Media>>(emptyList()) }
                LaunchedEffect(mimeFilter) {
                    val all = mediaDao.getLocalPickable(500)
                    items = all.filter { matchesMime(it.mimeType, mimeFilter) }
                }

                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.pick_media_title)) },
                            navigationIcon = {
                                IconButton(onClick = {
                                    setResult(Activity.RESULT_CANCELED)
                                    finish()
                                }) {
                                    Icon(
                                        painter = painterResource(R.drawable.arrow_back),
                                        contentDescription = stringResource(R.string.cancel)
                                    )
                                }
                            }
                        )
                    }
                ) { padding ->
                    if (items.isEmpty()) {
                        Box(
                            Modifier
                                .fillMaxSize()
                                .padding(padding),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(stringResource(R.string.pick_media_empty))
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(100.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(padding),
                            contentPadding = PaddingValues(2.dp),
                            horizontalArrangement = Arrangement.spacedBy(2.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            items(items, key = { it.id }) { media ->
                                PickerTile(media) {
                                    finishWithSelection(listOf(media), allowMultiple)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun finishWithSelection(selected: List<Media>, allowMultiple: Boolean) {
        if (selected.isEmpty()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val uris = selected.mapNotNull { resolveShareUri(it) }
        if (uris.isEmpty()) {
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }
        val result = Intent().apply {
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (allowMultiple && uris.size > 1) {
                clipData = ClipData.newUri(contentResolver, "media", uris.first()).also { clip ->
                    uris.drop(1).forEach { uri ->
                        clip.addItem(ClipData.Item(uri))
                    }
                }
                type = intent.type ?: "*/*"
            } else {
                data = uris.first()
                type = selected.first().mimeType.ifBlank { intent.type ?: "*/*" }
            }
        }
        setResult(Activity.RESULT_OK, result)
        finish()
    }

    private fun resolveShareUri(media: Media): Uri? {
        media.uri?.let { return it }
        return GalleryDocumentsProvider.documentUri(media.id)
    }

    private fun matchesMime(mime: String, filter: String?): Boolean {
        if (filter.isNullOrBlank()) return true
        if (filter == "*/*") return true
        if (filter.endsWith("/*")) {
            val prefix = filter.removeSuffix("/*")
            return mime.startsWith("$prefix/")
        }
        return mime.equals(filter, ignoreCase = true)
    }
}

@Composable
private fun PickerTile(media: Media, onClick: () -> Unit) {
    val context = LocalContext.current
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(media.uri)
            .crossfade(true)
            .build(),
        contentDescription = media.filename,
        contentScale = ContentScale.Crop,
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clickable(onClick = onClick)
    )
}
