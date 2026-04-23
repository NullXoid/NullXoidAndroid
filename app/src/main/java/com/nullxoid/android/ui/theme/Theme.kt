package com.nullxoid.android.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9AB7FF),
    onPrimary = Color(0xFF002D73),
    background = Color(0xFF0F1115),
    surface = Color(0xFF151922)
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF2F54D8),
    onPrimary = Color(0xFFFFFFFF),
    background = Color(0xFFF7F8FB),
    surface = Color(0xFFFFFFFF)
)

@Composable
fun NullXoidTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val scheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context)
            else dynamicLightColorScheme(context)
        darkTheme -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        val window = (view.context as? Activity)?.window
        window?.statusBarColor = scheme.background.value.toInt()
    }

    MaterialTheme(colorScheme = scheme, content = content)
}
