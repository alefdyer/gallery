package com.asinosoft.photogallery.ui.component

import android.app.Activity
import android.app.KeyguardManager
import android.content.Context
import android.hardware.biometrics.BiometricManager.Authenticators
import android.hardware.biometrics.BiometricPrompt
import android.os.Build
import android.os.CancellationSignal
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.asinosoft.photogallery.R

/**
 * Возвращает функцию, которая просит подтвердить владельца телефона
 * (отпечаток, лицо или PIN) и при успехе вызывает [onSuccess].
 * Если блокировка экрана не настроена, проверять нечем и [onSuccess] вызывается сразу.
 */
@Composable
fun rememberDeviceAuth(onSuccess: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val title = stringResource(R.string.private_album)
    val currentOnSuccess by rememberUpdatedState(onSuccess)
    val keyguardLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Activity.RESULT_OK == it.resultCode) currentOnSuccess()
    }

    return remember(context, title) {
        {
            val keyguard = context.getSystemService(KeyguardManager::class.java)
            when {
                !keyguard.isDeviceSecure -> currentOnSuccess()
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.P -> showBiometricPrompt(context, title) { currentOnSuccess() }
                else -> {
                    @Suppress("DEPRECATION")
                    val intent = keyguard.createConfirmDeviceCredentialIntent(title, null)
                    if (null != intent) keyguardLauncher.launch(intent) else currentOnSuccess()
                }
            }
        }
    }
}

private fun showBiometricPrompt(context: Context, title: String, onSuccess: () -> Unit) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
    val builder = BiometricPrompt.Builder(context).setTitle(title)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        builder.setAllowedAuthenticators(Authenticators.BIOMETRIC_WEAK or Authenticators.DEVICE_CREDENTIAL)
    } else {
        @Suppress("DEPRECATION")
        builder.setDeviceCredentialAllowed(true)
    }
    builder.build().authenticate(
        CancellationSignal(),
        context.mainExecutor,
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }
        }
    )
}
