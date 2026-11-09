package com.asinosoft.photogallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.asinosoft.photogallery.ui.component.SettingsPage
import com.asinosoft.photogallery.ui.component.TemporaryFilesPanel
import com.asinosoft.photogallery.ui.theme.GalleryTheme

/** Системное «Управление местом» → тот же список временных файлов, что в настройках. */
class ManageSpaceActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            GalleryTheme {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(stringResource(R.string.temporary_files)) },
                            navigationIcon = {
                                IconButton(onClick = ::finish) {
                                    Icon(
                                        painter = painterResource(R.drawable.arrow_back),
                                        contentDescription = stringResource(R.string.close),
                                    )
                                }
                            },
                        )
                    },
                ) { padding ->
                    SettingsPage(
                        Modifier
                            .fillMaxSize()
                            .padding(padding),
                    ) {
                        TemporaryFilesPanel()
                    }
                }
            }
        }
    }
}
