package com.asinosoft.gallery.data.storage.yandex

import android.net.Uri
import android.util.Base64
import androidx.core.net.toUri
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.security.MessageDigest
import java.security.SecureRandom
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

object YandexOAuth {
    const val CLIENT_ID = "a4d15c7dab2741ccadadfd950838f18f"

    /** Must match a Redirect URI registered for this client in Yandex OAuth (Web platform). */
    const val REDIRECT_URI = "com.asinosoft.gallery://yandex/oauth"

    private const val AUTHORIZE_HOST = "https://oauth.yandex.com/authorize"
    private const val TOKEN_URL = "https://oauth.yandex.com/token"

    private data class PkcePending(val codeVerifier: String, val state: String)

    private var pending: PkcePending? = null

    fun isRedirect(uri: Uri?): Boolean = uri != null &&
        uri.scheme == "com.asinosoft.gallery" &&
        uri.host == "yandex" &&
        uri.path?.startsWith("/oauth") == true

    /**
     * Starts a new PKCE session and returns the authorize URL to open in a browser (e.g. Custom Tabs).
     */
    fun startAuthorization(): Uri {
        val verifier = generateCodeVerifier()
        val state = generateState()
        pending = PkcePending(verifier, state)
        val challenge = codeChallengeS256(verifier)
        return AUTHORIZE_HOST.toUri().buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", CLIENT_ID)
            .appendQueryParameter("redirect_uri", REDIRECT_URI)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .build()
    }

    fun cancelPending() {
        pending = null
    }

    /**
     * Validates the redirect [Uri], exchanges the code for tokens, and clears PKCE state.
     */
    suspend fun completeAuthorization(uri: Uri): Result<String> {
        val code = uri.getQueryParameter("code")
        val state = uri.getQueryParameter("state")
        uri.getQueryParameter("error")?.let { err ->
            val desc = uri.getQueryParameter("error_description").orEmpty()
            val message = listOf(err, desc).filter { it.isNotBlank() }.joinToString(": ")
            return Result.failure(IllegalStateException(message))
        }
        if (code.isNullOrBlank()) {
            return Result.failure(IllegalStateException("missing code"))
        }
        val p = pending ?: return Result.failure(IllegalStateException("no pending OAuth session"))
        if (state != p.state) {
            cancelPending()
            return Result.failure(IllegalStateException("state mismatch"))
        }
        pending = null
        return exchangeCodeForToken(code, p.codeVerifier)
    }

    private suspend fun exchangeCodeForToken(code: String, codeVerifier: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val body = FormBody.Builder()
                    .add("grant_type", "authorization_code")
                    .add("code", code)
                    .add("client_id", CLIENT_ID)
                    .add("code_verifier", codeVerifier)
                    .build()
                val request = Request.Builder()
                    .url(TOKEN_URL)
                    .post(body)
                    .build()
                OkHttpClient().newCall(request).execute().use { response ->
                    val raw = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val err = runCatching {
                            Gson().fromJson(raw, YandexOAuthErrorJson::class.java)
                        }.getOrNull()
                        val msg = err?.let { e ->
                            listOfNotNull(e.error, e.errorDescription).joinToString(": ")
                        }.takeIf { !it.isNullOrBlank() } ?: raw.ifBlank { "HTTP ${response.code}" }
                        return@withContext Result.failure(IllegalStateException(msg))
                    }
                    val token = Gson().fromJson(raw, YandexTokenJson::class.java).accessToken
                    if (token.isBlank()) {
                        Result.failure(IllegalStateException("empty access_token"))
                    } else {
                        Result.success(token)
                    }
                }
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun generateState(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun codeChallengeS256(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(
            digest,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
        )
    }

    private data class YandexTokenJson(
        @SerializedName("access_token")
        val accessToken: String
    )

    private data class YandexOAuthErrorJson(
        @SerializedName("error")
        val error: String?,
        @SerializedName("error_description")
        val errorDescription: String?
    )
}

sealed class YandexOAuthEvent {
    data class Success(val accessToken: String) : YandexOAuthEvent()

    data class Failure(val message: String) : YandexOAuthEvent()
}

object YandexOAuthBus {
    private val _events = MutableSharedFlow<YandexOAuthEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<YandexOAuthEvent> = _events

    suspend fun emit(event: YandexOAuthEvent) {
        _events.emit(event)
    }
}
