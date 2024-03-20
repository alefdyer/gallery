package com.asinosoft.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.navigation.compose.rememberNavController
import com.asinosoft.gallery.data.ImageFetcher
import com.asinosoft.gallery.job.MediaObserver
import com.asinosoft.gallery.ui.Navigation
import com.asinosoft.gallery.ui.PermissionDisclaimer
import com.asinosoft.gallery.ui.theme.GalleryTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject
    lateinit var fetcher: ImageFetcher

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) Manifest.permission.READ_MEDIA_IMAGES
            else Manifest.permission.READ_EXTERNAL_STORAGE

        if (PackageManager.PERMISSION_GRANTED == checkSelfPermission(permission)) {
            startFetcher()
        }

        setContent {
            val storagePermission = rememberPermissionState(permission) { granted ->
                if (granted) startFetcher()
            }

            val navController = rememberNavController()

            GalleryTheme {
                when (storagePermission.status.isGranted) {
                    true -> Navigation(navController = navController)

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

    private fun startFetcher() {
        runBlocking(Dispatchers.IO) {
            fetcher.fetchAll()
        }
    }
}
