package com.nullxoid.android

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.nullxoid.android.ui.NullXoidApp

class MainActivity : ComponentActivity() {
    private var oidcRedirect by mutableStateOf<Uri?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        oidcRedirect = intent?.data
        val app = application as NullXoidApplication
        setContent {
            NullXoidApp(
                app = app,
                oidcRedirect = oidcRedirect,
                onOidcRedirectConsumed = { oidcRedirect = null }
            )
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        oidcRedirect = intent.data
    }
}
