package com.asinosoft.gallery

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity

class AuthRedirectActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val redirectUri = intent?.data
        redirectUri?.let(OAuthRedirectBus::publish) ?: run {
            finish()
            return
        }

        startActivity(
            Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
        )
        finish()
    }
}
