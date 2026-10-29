package com.asinosoft.photogallery.data.storage.onedrive

import android.net.Uri
import android.util.Base64
import androidx.core.net.toUri
import com.asinosoft.photogallery.BuildConfig
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request

object OneDriveApi {
    const val GRAPH_BASE = "https://graph.microsoft.com/v1.0"
    private const val AUTHORIZE_URL =
        "https://login.microsoftonline.com/common/oauth2/v2.0/authorize"
    private const val TOKEN_URL = "https://login.microsoftonline.com/common/oauth2/v2.0/token"
    private const val SCOPES = "Files.Read User.Read offline_access"

    private lateinit var codeVerifier: String
    private lateinit var state: String
    private val delivered = AtomicBoolean(false)

    fun startAuthorization(): Uri {
        delivered.set(false)
        codeVerifier = generateCodeVerifier()
        state = UUID.randomUUID().toString()
        val challenge = codeChallengeS256(codeVerifier)
        return AUTHORIZE_URL.toUri().buildUpon()
            .appendQueryParameter("client_id", BuildConfig.ONEDRIVE_CLIENT_ID)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("redirect_uri", BuildConfig.ONEDRIVE_REDIRECT_URI)
            .appendQueryParameter("response_mode", "query")
            .appendQueryParameter("scope", SCOPES)
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", state)
            .appendQueryParameter("prompt", "select_account")
            .build()
    }

    suspend fun getTokenFromUrl(uri: Uri): Result<TokenResult> = runCatching {
        val url = uri.toString()
        if (!url.startsWith(BuildConfig.ONEDRIVE_REDIRECT_URI)) {
            error("Unexpected redirect")
        }
        if (!delivered.compareAndSet(false, true)) error("Already handled")
        val code = uri.getQueryParameter("code") ?: error("No code")
        val returnedState = uri.getQueryParameter("state")
        if (returnedState != state) error("State mismatch")
        if (BuildConfig.ONEDRIVE_CLIENT_ID.isBlank()) {
            error("onedrive.clientId is missing in local.properties")
        }

        withContext(Dispatchers.IO) {
            val body = FormBody.Builder()
                .add("client_id", BuildConfig.ONEDRIVE_CLIENT_ID)
                .add("grant_type", "authorization_code")
                .add("code", code)
                .add("redirect_uri", BuildConfig.ONEDRIVE_REDIRECT_URI)
                .add("code_verifier", codeVerifier)
                .build()
            val request = Request.Builder().url(TOKEN_URL).post(body).build()
            OkHttpClient().newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) error("Token HTTP ${response.code}: $payload")
                val token = Gson().fromJson(payload, TokenResponse::class.java)
                val access = token.accessToken ?: error("No access_token")
                val email = fetchEmail(access)
                TokenResult(accessToken = access, email = email)
            }
        }
    }

    private fun fetchEmail(accessToken: String): String? {
        val request = Request.Builder()
            .url("$GRAPH_BASE/me?\$select=userPrincipalName,mail")
            .header("Authorization", "Bearer $accessToken")
            .get()
            .build()
        return try {
            OkHttpClient().newCall(request).execute().use { response ->
                if (!response.isSuccessful) return null
                val me = Gson().fromJson(response.body?.string(), MeResponse::class.java)
                me.mail ?: me.userPrincipalName
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    private fun codeChallengeS256(verifier: String): String {
        val digest =
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII))
        return Base64.encodeToString(digest, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    data class TokenResult(val accessToken: String, val email: String?)

    private data class TokenResponse(
        @SerializedName("access_token") val accessToken: String?,
        @SerializedName("refresh_token") val refreshToken: String?
    )

    private data class MeResponse(
        val mail: String? = null,
        val userPrincipalName: String? = null
    )
}
