package app.playgram.core.design

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class PlaygramColors(
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val surfaceElevated: Color,
    val border: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val statusSuccess: Color,
    val statusWarning: Color,
    val statusError: Color,
    val statusInfo: Color
)

val LocalPlaygramColors = staticCompositionLocalOf {
    PlaygramColors(
        background = BackgroundDark,
        surface = SurfaceDark,
        surfaceVariant = SurfaceVariantDark,
        surfaceElevated = SurfaceElevatedDark,
        border = BorderDark,
        accent = PlaygramAccent.BLUE.color,
        textPrimary = TextPrimary,
        textSecondary = TextSecondary,
        textTertiary = TextTertiary,
        statusSuccess = StatusSuccess,
        statusWarning = StatusWarning,
        statusError = StatusError,
        statusInfo = StatusInfo
    )
}

@Composable
fun PlaygramTheme(
    amoled: Boolean = false,
    accent: PlaygramAccent = PlaygramAccent.BLUE,
    content: @Composable () -> Unit
) {
    val bg = if (amoled) BlackAmoled else BackgroundDark
    val surf = if (amoled) SurfaceAmoled else SurfaceDark
    val surfVar = if (amoled) SurfaceDark else SurfaceVariantDark
    val accentCol = accent.color

    val customColors = PlaygramColors(
        background = bg,
        surface = surf,
        surfaceVariant = surfVar,
        surfaceElevated = SurfaceElevatedDark,
        border = BorderDark,
        accent = accentCol,
        textPrimary = TextPrimary,
        textSecondary = TextSecondary,
        textTertiary = TextTertiary,
        statusSuccess = StatusSuccess,
        statusWarning = StatusWarning,
        statusError = StatusError,
        statusInfo = StatusInfo
    )

    val materialScheme = darkColorScheme(
        primary = accentCol,
        onPrimary = Color.White,
        primaryContainer = AccentContainer,
        onPrimaryContainer = AccentBlueLight,
        background = bg,
        onBackground = TextPrimary,
        surface = surf,
        onSurface = TextPrimary,
        surfaceVariant = surfVar,
        onSurfaceVariant = TextSecondary,
        outline = BorderDark
    )

    CompositionLocalProvider(LocalPlaygramColors provides customColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = KinoTypography,
            content = content
        )
    }
}
