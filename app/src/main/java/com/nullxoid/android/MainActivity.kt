package com.nullxoid.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.nullxoid.android.ui.NullXoidApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as NullXoidApplication
        setContent { NullXoidApp(app) }
    }
}
