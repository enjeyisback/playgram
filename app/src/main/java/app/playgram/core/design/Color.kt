package app.playgram.core.design

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color

val BackgroundDark = Color(0xFF0F1419)
val SurfaceDark = Color(0xFF141C24)
val SurfaceVariantDark = Color(0xFF17202A)
val SurfaceElevatedDark = Color(0xFF1F2A36)
val BorderDark = Color(0xFF2A3644)

val TextPrimary = Color(0xFFE6EDF3)
val TextSecondary = Color(0xFF8394A6)
val TextTertiary = Color(0xFF6B7B8C)

val BlackAmoled = Color(0xFF000000)
val SurfaceAmoled = Color(0xFF0D0D0D)

// Accent Color Presets verified from Playgram inspection
val AccentCrimson = Color(0xFFE53935)
val AccentCyan = Color(0xFF00BCD4)
val AccentEmerald = Color(0xFF4CAF50)
val AccentAmber = Color(0xFFFFB300)
val AccentViolet = Color(0xFF9C27B0)

/**
 * The chosen accent. The three colours below read it, so every screen that draws with them
 * recolours as soon as the user picks another accent in Settings.
 */
object AccentPrefs {
    private const val KEY = "accent"
    var current by mutableStateOf(PlaygramAccent.BLUE)
        private set

    fun load(context: Context) {
        val name = context.getSharedPreferences("playgram_settings", Context.MODE_PRIVATE).getString(KEY, null)
        current = PlaygramAccent.values().firstOrNull { it.name == name } ?: PlaygramAccent.BLUE
    }

    fun set(context: Context, accent: PlaygramAccent) {
        current = accent
        context.getSharedPreferences("playgram_settings", Context.MODE_PRIVATE).edit().putString(KEY, accent.name).apply()
    }
}

/** Buttons, selected tabs, filled controls. */
val AccentBlue: Color get() = AccentPrefs.current.color
/** Icons, links and progress on dark backgrounds. */
val AccentBlueLight: Color get() = AccentPrefs.current.light
/** Tinted backgrounds behind accent icons. */
val AccentContainer: Color get() = AccentPrefs.current.container
val AvatarPink = Color(0xFFF49AC1)
val StatusOnline = Color(0xFF3DD68C)
val WarningAmber = Color(0xFFF5A03C)

val StatusSuccess = Color(0xFF2EA043)
val StatusWarning = Color(0xFFD29922)
val StatusError = Color(0xFFF85149)
val StatusInfo = Color(0xFF58A6FF)

enum class PlaygramAccent(val title: String, val color: Color, val light: Color, val container: Color) {
    BLUE("Blue", Color(0xFF2B7FD9), Color(0xFF5AB0FF), Color(0xFF1E334C)),
    CRIMSON("Crimson", AccentCrimson, Color(0xFFFF7A75), Color(0xFF4A1F22)),
    CYAN("Cyan", Color(0xFF0097A7), Color(0xFF4DD9EA), Color(0xFF123B40)),
    EMERALD("Emerald", Color(0xFF2E9E4F), Color(0xFF6FD98B), Color(0xFF173A24)),
    AMBER("Amber", Color(0xFFD99400), Color(0xFFFFC84D), Color(0xFF453516)),
    VIOLET("Violet", Color(0xFF8E3FB8), Color(0xFFCC8BF0), Color(0xFF35204A))
}
