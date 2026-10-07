package com.asinosoft.gallery

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.navigation.compose.rememberNavController
import com.asinosoft.gallery.di.IntentHelper
import com.asinosoft.gallery.model.MainViewModel
import com.asinosoft.gallery.ui.Navigation
import com.asinosoft.gallery.ui.PermissionDisclaimer
import com.asinosoft.gallery.ui.theme.GalleryTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: MainViewModel by viewModels()
    private val intentHelper = IntentHelper

    private val mediaPermissions =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            listOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
        } else {
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }

    private fun hasMediaPermissions() = mediaPermissions.all {
        PackageManager.PERMISSION_GRANTED == checkSelfPermission(it)
    }

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(intentHelper)

        if (hasMediaPermissions()) {
            model.start()
        }

        setContent {
            val storagePermissions =
                rememberMultiplePermissionsState(mediaPermissions) { result ->
                    if (result.values.all { it }) model.start()
                }

            val navController = rememberNavController()

            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(model.messages) {
                model.messages.collect(snackbarHostState::showSnackbar)
            }

            GalleryTheme {
                when (storagePermissions.allPermissionsGranted) {
                    true -> {
                        Navigation(navController)
                    }

                    else -> {
                        Box {
                            PermissionDisclaimer(storagePermissions)
                        }
                    }
                }

                SnackbarHost(snackbarHostState)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasMediaPermissions()) {
            model.start()
        }
    }
}
