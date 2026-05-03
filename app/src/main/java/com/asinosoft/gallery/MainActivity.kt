package com.asinosoft.gallery

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.asinosoft.gallery.data.storage.yandex.YandexOAuth
import com.asinosoft.gallery.data.storage.yandex.YandexOAuthBus
import com.asinosoft.gallery.data.storage.yandex.YandexOAuthEvent
import com.asinosoft.gallery.di.IntentHelper
import com.asinosoft.gallery.model.GalleryViewModel
import com.asinosoft.gallery.ui.Navigation
import com.asinosoft.gallery.ui.PermissionDisclaimer
import com.asinosoft.gallery.ui.theme.GalleryTheme
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    private val model: GalleryViewModel by viewModels()
    private val intentHelper = IntentHelper

    @OptIn(ExperimentalPermissionsApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lifecycle.addObserver(intentHelper)

        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }

        if (PackageManager.PERMISSION_GRANTED == checkSelfPermission(permission)) {
            model.start()
        }

        handleYandexOAuthRedirect(intent)

        setContent {
            val storagePermission =
                rememberPermissionState(permission) { granted ->
                    if (granted) model.start()
                }

            val navController = rememberNavController()

            val snackbarHostState = remember { SnackbarHostState() }

            LaunchedEffect(model.message) {
                launch {
                    model.message.filterNotNull().collect {
                        model.clearMessage()
                        snackbarHostState.showSnackbar(it)
                    }
                }
            }

            GalleryTheme {
                when (storagePermission.status.isGranted) {
                    true -> {
                        Navigation(navController, model)
                    }

                    else -> {
                        Box {
                            PermissionDisclaimer(storagePermission)
                        }
                    }
                }

                SnackbarHost(snackbarHostState)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleYandexOAuthRedirect(intent)
    }

    private fun handleYandexOAuthRedirect(intent: Intent?) {
        Log.d("activity", "Url = ${intent?.data}")
        val uri = intent?.data ?: return
        if (!YandexOAuth.isRedirect(uri)) return
        lifecycleScope.launch {
            val result = YandexOAuth.completeAuthorization(uri)
            Log.d("activity", "Result: $result")
            result.fold(
                onSuccess = { token ->
                    YandexOAuthBus.emit(YandexOAuthEvent.Success(token))
                },
                onFailure = { e ->
                    YandexOAuthBus.emit(
                        YandexOAuthEvent.Failure(e.message ?: e.toString())
                    )
                }
            )
        }
    }
}
