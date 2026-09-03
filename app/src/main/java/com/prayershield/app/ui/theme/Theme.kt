package com.prayershield.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val SleepShieldColorScheme = darkColorScheme(
    primary = SleepShieldPrimary,
    background = SleepShieldBackground,
    surface = SleepShieldSurface,
    onSurface = SleepShieldOnSurface,
    onSurfaceVariant = SleepShieldOnSurfaceVariant
)

private val AmoledColorScheme = darkColorScheme(
    primary = SleepShieldPrimary,
    background = AmoledBlack,
    surface = AmoledSurface,
    onSurface = SleepShieldOnSurface,
    onSurfaceVariant = SleepShieldOnSurfaceVariant
)

@Composable
fun PrayerShieldTheme(
    useAmoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (useAmoled) AmoledColorScheme else SleepShieldColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            window.navigationBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content
    )
}
