package dev.yernur.moneytracker.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** System (wallpaper-derived) colors; minSdk 36 so dynamic color is always available. */
@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    MaterialTheme(colorScheme = colors, content = content)
}

/** Over-limit / expense / income colors are fixed on purpose, so they stay recognizable whatever the system accent is. */
object Semantic {
    val income = Color(0xFF2E7D32)
    val expense = Color(0xFFC62828)
}
