package com.asinosoft.gallery

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.asinosoft.gallery.ui.MainView
import com.asinosoft.gallery.ui.PermissionDisclaimer
import com.asinosoft.gallery.ui.theme.GalleryTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            val storagePermission =
                rememberPermissionState(Manifest.permission.READ_EXTERNAL_STORAGE)

            GalleryTheme {
                when (storagePermission.status.isGranted) {
                    true -> MainView()

                    else -> PermissionDisclaimer {
                        storagePermission.launchPermissionRequest()
                    }
                }
            }
        }
    }
}
