package com.example.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

private val CleanLightColorScheme = lightColorScheme(
    primary = InkPrimary,
    onPrimary = Color.White,
    primaryContainer = CobaltSoft,
    onPrimaryContainer = CobaltOnSoft,
    secondary = CobaltAccent,
    onSecondary = Color.White,
    secondaryContainer = PorcelainSurfaceHigh,
    onSecondaryContainer = InkPrimary,
    tertiary = EmeraldLive,
    onTertiary = Color.White,
    background = PorcelainBg,
    onBackground = InkPrimary,
    surface = PorcelainSurface,
    onSurface = InkPrimary,
    surfaceVariant = PorcelainSurfaceLow,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = PorcelainBg,
    surfaceContainerLow = PorcelainSurfaceLow,
    surfaceContainer = PorcelainSurface,
    surfaceContainerHigh = PorcelainSurfaceHigh,
    surfaceContainerHighest = HairlineLight,
    outline = HairlineLight,
    outlineVariant = HairlineLight,
    error = ErrorRed,
    onError = Color.White
)

private val CleanDarkColorScheme = darkColorScheme(
    primary = CyanAccent,
    onPrimary = Color(0xFF042029),
    primaryContainer = CyanSoftDark,
    onPrimaryContainer = CyanOnSoftDark,
    secondary = CyanAccent,
    onSecondary = Color(0xFF042029),
    secondaryContainer = ObsidianSurfaceHigh,
    onSecondaryContainer = IceWhite,
    tertiary = EmeraldLive,
    onTertiary = ObsidianBg,
    background = ObsidianBg,
    onBackground = IceWhite,
    surface = ObsidianSurface,
    onSurface = IceWhite,
    surfaceVariant = ObsidianSurfaceHigh,
    onSurfaceVariant = SlateMuted,
    surfaceContainerLowest = ObsidianBg,
    surfaceContainerLow = ObsidianSurfaceLow,
    surfaceContainer = ObsidianSurface,
    surfaceContainerHigh = ObsidianSurfaceHigh,
    surfaceContainerHighest = HairlineDark,
    outline = HairlineDark,
    outlineVariant = HairlineDark,
    error = ErrorRed,
    onError = Color.White
)

val LiveHtmlShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

@Composable
fun LiveHtmlTheme(
    darkTheme: Boolean = false,
    content: @Composable () -> Unit
) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = if (darkTheme) CleanDarkColorScheme else CleanLightColorScheme,
        typography = AppTypography,
        shapes = LiveHtmlShapes,
        content = content
    )
}
