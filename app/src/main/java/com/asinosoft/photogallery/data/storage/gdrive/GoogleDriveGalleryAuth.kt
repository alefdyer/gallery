package com.asinosoft.photogallery.data.storage.gdrive

import android.app.Activity
import android.app.PendingIntent
import androidx.activity.result.ActivityResult
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/** OAuth для галереи Google Drive (отдельно от backup App Data). */
object GoogleDriveGalleryAuth {
    fun authorizationRequest(): AuthorizationRequest =
        AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GoogleDriveStorageProvider.DRIVE_READONLY_SCOPE)))
            .build()

    suspend fun beginAuthorize(activity: Activity): AuthStart {
        val result = Identity.getAuthorizationClient(activity)
            .authorize(authorizationRequest())
            .await()
        if (result.hasResolution()) {
            val pending = result.pendingIntent
                ?: error("Authorization resolution without PendingIntent")
            return AuthStart.NeedsUi(pending)
        }
        val token = result.accessToken
        if (token.isNullOrBlank()) error("No access token after authorize()")
        return AuthStart.Success(token)
    }

    suspend fun handleAuthorizeResult(
        activity: Activity,
        result: ActivityResult
    ): AuthOutcome = withContext(Dispatchers.IO) {
        if (result.resultCode == Activity.RESULT_CANCELED && result.data == null) {
            return@withContext AuthOutcome.Cancelled
        }
        if (result.data != null) {
            try {
                val auth = Identity.getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(result.data)
                val token = auth.accessToken
                if (!token.isNullOrBlank()) {
                    return@withContext AuthOutcome.Success(token)
                }
            } catch (e: ApiException) {
                return@withContext mapApiException(e)
            }
        } else if (result.resultCode != Activity.RESULT_OK) {
            return@withContext AuthOutcome.Cancelled
        }

        try {
            when (val again = beginAuthorize(activity)) {
                is AuthStart.Success -> AuthOutcome.Success(again.token)
                is AuthStart.NeedsUi ->
                    AuthOutcome.Failed("Consent UI returned without token")
            }
        } catch (e: ApiException) {
            mapApiException(e)
        } catch (e: Exception) {
            AuthOutcome.Failed(e.message ?: "Authorization failed")
        }
    }

    private fun mapApiException(e: ApiException): AuthOutcome = when {
        e.statusCode == CommonStatusCodes.CANCELED -> AuthOutcome.Cancelled
        else -> AuthOutcome.Failed(
            e.message?.takeIf { it.isNotBlank() } ?: "Google auth error ${e.statusCode}"
        )
    }

    sealed interface AuthStart {
        data class NeedsUi(val pendingIntent: PendingIntent) : AuthStart
        data class Success(val token: String) : AuthStart
    }

    sealed interface AuthOutcome {
        data class Success(val token: String) : AuthOutcome
        data object Cancelled : AuthOutcome
        data class Failed(val message: String) : AuthOutcome
    }
}
