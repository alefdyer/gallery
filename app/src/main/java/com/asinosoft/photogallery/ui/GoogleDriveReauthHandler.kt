package com.asinosoft.photogallery.ui

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import com.asinosoft.photogallery.data.storage.gdrive.GoogleDriveGalleryAuth
import com.asinosoft.photogallery.model.DriveReauthViewModel
import kotlinx.coroutines.launch

/**
 * Когда тихий refresh Drive упирается в hasResolution(), показывает экран Google
 * и сохраняет новый токен — без ручного отключения в «Сеть».
 */
@Composable
fun GoogleDriveReauthHandler(
    model: DriveReauthViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    var pendingStorageId by remember { mutableLongStateOf(-1L) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val host = activity ?: return@rememberLauncherForActivityResult
        val storageId = pendingStorageId
        if (storageId < 0) return@rememberLauncherForActivityResult
        scope.launch {
            when (val outcome = GoogleDriveGalleryAuth.handleAuthorizeResult(host, result)) {
                is GoogleDriveGalleryAuth.AuthOutcome.Success ->
                    model.applyToken(storageId, outcome.token)

                else -> model.cancel(storageId)
            }
            pendingStorageId = -1L
        }
    }

    LaunchedEffect(model) {
        model.requests.collect { request ->
            pendingStorageId = request.storageId
            runCatching {
                launcher.launch(
                    IntentSenderRequest.Builder(request.pendingIntent.intentSender).build()
                )
            }.onFailure {
                model.cancel(request.storageId)
                pendingStorageId = -1L
            }
        }
    }
}
