package com.audoneout.app.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object AudColors {
    val Ink = Color(0xFF101113)
    val Surface = Color(0xFF191B1E)
    val SurfaceRaised = Color(0xFF22252A)
    val Border = Color(0xFF34383F)
    val Coral = Color(0xFFF06B5C)
    val Teal = Color(0xFF4CB6A7)
    val Amber = Color(0xFFF1B951)
    val Violet = Color(0xFF9A86D8)
    val Text = Color(0xFFF5F2EE)
    val TextSecondary = Color(0xFFB8BBC1)
    val TextMuted = Color(0xFF858A92)
    val Error = Color(0xFFFF8A80)
}

private val DarkColors = darkColorScheme(
    primary = AudColors.Coral,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF53211D),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = AudColors.Teal,
    onSecondary = Color(0xFF05201C),
    tertiary = AudColors.Amber,
    background = AudColors.Ink,
    onBackground = AudColors.Text,
    surface = AudColors.Surface,
    onSurface = AudColors.Text,
    surfaceVariant = AudColors.SurfaceRaised,
    onSurfaceVariant = AudColors.TextSecondary,
    outline = AudColors.Border,
    error = AudColors.Error
)

private val LightColors = lightColorScheme(
    primary = Color(0xFFB94034),
    secondary = Color(0xFF18786D),
    tertiary = Color(0xFF835500),
    background = Color(0xFFFAF8F5),
    surface = Color.White,
    surfaceVariant = Color(0xFFF0ECE7),
    outline = Color(0xFFCBC5BE)
)

@Composable
fun AudOneOutTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = MaterialTheme.typography,
        content = content
    )
}
