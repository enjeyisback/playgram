package app.playgram.core.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider

/** Players offered by "Open in another player"; an empty package list means Android's own app chooser. */
data class ExternalPlayer(val name: String, val packages: List<String>)

object ExternalPlayers {
    const val BUILT_IN = "Playgram"
    const val PREF_KEY = "default_player"

    val all = listOf(
        ExternalPlayer("System default", emptyList()),
        ExternalPlayer("VLC", listOf("org.videolan.vlc")),
        ExternalPlayer("MX Player", listOf("com.mxtech.videoplayer.pro", "com.mxtech.videoplayer.ad")),
        ExternalPlayer("mpv", listOf("is.xyz.mpv"))
    )

    fun installedPackage(context: Context, player: ExternalPlayer): String? = player.packages.firstOrNull { pkg ->
        try { context.packageManager.getPackageInfo(pkg, 0); true } catch (_: Exception) { false }
    }

    fun mimeFor(title: String): String = when (title.substringAfterLast('.', "").lowercase()) {
        "mkv" -> "video/x-matroska"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "avi" -> "video/x-msvideo"
        "mov" -> "video/quicktime"
        "ts" -> "video/mp2t"
        else -> "video/*"
    }

    /** What Settings → Default player is set to: [BUILT_IN] or the name of an [all] entry. */
    fun chosenName(context: Context): String =
        context.getSharedPreferences("playgram_settings", Context.MODE_PRIVATE).getString(PREF_KEY, BUILT_IN) ?: BUILT_IN

    /** The external player every video should open in, or null when Playgram's own player is chosen (or the app is gone). */
    fun defaultExternal(context: Context): ExternalPlayer? {
        val player = all.firstOrNull { it.name == chosenName(context) } ?: return null
        return if (player.packages.isEmpty() || installedPackage(context, player) != null) player else null
    }

    /**
     * The VIEW intent that hands a video to another player. Telegram files go through the local stream
     * server, saved files through the FileProvider. [resumeAt] is in ms.
     */
    fun viewIntent(
        context: Context, videoUrl: String, playUrl: String, title: String, resumeAt: Long, pkg: String?,
        forceChooser: Boolean = false
    ): Intent {
        // "System default": once a player was picked in Android's chooser, keep using it.
        val target = pkg ?: if (forceChooser) null else rememberedPackage(context)
        val mime = mimeFor(title)
        val uri = when {
            videoUrl.startsWith("/") -> FileProvider.getUriForFile(context, "app.playgram.downloads.files", java.io.File(videoUrl))
            videoUrl.startsWith("tg://") -> Uri.parse(StreamServer.urlFor(context, playUrl, title, mime))
            else -> Uri.parse(videoUrl)
        }
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .putExtra("title", title)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        // VLC and mpv read a Long, MX Player an Int (both in ms).
        if (resumeAt > 5_000) {
            if (target?.startsWith("com.mxtech") == true) view.putExtra("position", resumeAt.toInt())
            else view.putExtra("position", resumeAt)
        }
        if (target != null) return view.setPackage(target)
        // The chooser reports what was picked to ChosenPlayerReceiver, which remembers it.
        val sender = android.app.PendingIntent.getBroadcast(
            context, 0, Intent(context, ChosenPlayerReceiver::class.java),
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
        ).intentSender
        return Intent.createChooser(view, "Open with", sender)
    }

    private const val REMEMBERED_KEY = "system_default_pkg"
    private fun prefs(context: Context) = context.getSharedPreferences("playgram_settings", Context.MODE_PRIVATE)

    /** The player picked last time in the system chooser, if it is still installed. */
    fun rememberedPackage(context: Context): String? {
        val pkg = prefs(context).getString(REMEMBERED_KEY, null) ?: return null
        return try { context.packageManager.getPackageInfo(pkg, 0); pkg } catch (_: Exception) { null }
    }

    fun rememberedLabel(context: Context): String? = rememberedPackage(context)?.let {
        try { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(it, 0)).toString() } catch (_: Exception) { it }
    }

    fun forgetRemembered(context: Context) { prefs(context).edit().remove(REMEMBERED_KEY).apply() }

    internal fun remember(context: Context, pkg: String) {
        if (pkg == context.packageName) {
            // Picking Playgram itself means: play in Playgram's own player.
            prefs(context).edit().putString(PREF_KEY, BUILT_IN).remove(REMEMBERED_KEY).apply()
        } else prefs(context).edit().putString(REMEMBERED_KEY, pkg).apply()
    }
}

/** Picture-in-picture state shared between the player screen and MainActivity. */
object PipState {
    /** A video screen is open. */
    @Volatile var active = false
    /** It is playing (or about to), so leaving the app should float it. */
    @Volatile var playing = false
    @Volatile var ratio = 16f / 9f
    /** Pauses the player when the floating window is closed. */
    @Volatile var pause: (() -> Unit)? = null
    var inPip by androidx.compose.runtime.mutableStateOf(false)

    fun params(autoEnter: Boolean): android.app.PictureInPictureParams {
        val r = ratio.coerceIn(0.45f, 2.2f)
        val b = android.app.PictureInPictureParams.Builder().setAspectRatio(android.util.Rational((r * 1000).toInt(), 1000))
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) b.setAutoEnterEnabled(autoEnter).setSeamlessResizeEnabled(true)
        return b.build()
    }

    fun enabledInSettings(context: Context): Boolean =
        context.getSharedPreferences("playgram_settings", Context.MODE_PRIVATE).getBoolean("pip_home", true)
}

/** Receives the player the user picked in the system chooser so "System default" can reuse it. */
class ChosenPlayerReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val chosen = intent.getParcelableExtra<android.content.ComponentName>(Intent.EXTRA_CHOSEN_COMPONENT) ?: return
        ExternalPlayers.remember(context, chosen.packageName)
    }
}
