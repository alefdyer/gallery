package com.asinosoft.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.navigation.compose.rememberNavController
import com.asinosoft.gallery.job.MediaObserver
import com.asinosoft.gallery.model.GalleryViewModel
import com.asinosoft.gallery.ui.Navigation
import com.asinosoft.gallery.ui.PermissionDisclaimer
import com.asinosoft.gallery.ui.theme.GalleryTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: GalleryViewModel by viewModels()

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE

        if (PackageManager.PERMISSION_GRANTED == checkSelfPermission(permission)) {
            model.rescan()
        }

        setContent {
            val storagePermission = rememberPermissionState(permission) { granted ->
                if (granted) model.rescan()
            }

            val navController = rememberNavController()

            GalleryTheme {
                when (storagePermission.status.isGranted) {
                    true -> Navigation(navController)

                    else -> Box {
                        PermissionDisclaimer(storagePermission)
                    }
                }
            }
        }

        if (!MediaObserver.isScheduled(this)) {
            MediaObserver.schedule(this)
        }
    }
}
