package app.playgram

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import app.playgram.core.design.PlaygramTheme
import app.playgram.core.player.PipState
import app.playgram.feature.player.PlayerScreen

/**
 * The player lives in its own task so it can float (picture-in-picture) while the app stays open behind it.
 */
class PlayerActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try { PlaygramApp.instance.appLockManager?.applySecurityFlags(this) } catch (_: Throwable) {}
        val url = intent.getStringExtra(EXTRA_URL) ?: run { finish(); return }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Media Player"
        setContent {
            PlaygramTheme(accent = app.playgram.core.design.AccentPrefs.current) {
                PlayerScreen(videoUrl = url, videoTitle = title, onBack = { finishAndRemoveTask() })
            }
        }
    }

    /** Home button / recents while a video plays: float it instead of stopping it. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            PipState.active && PipState.playing && PipState.enabledInSettings(this) && !isInPictureInPictureMode
        ) {
            try { enterPictureInPictureMode(PipState.params(false)) } catch (e: Throwable) { android.util.Log.e("PlayerActivity", "PiP failed", e) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        PipState.inPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        // The floating window was closed (a dismissed window stops the activity while still in PiP mode).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInPictureInPictureMode) {
            PipState.pause?.invoke()
            finishAndRemoveTask()
        }
    }

    companion object {
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"

        fun intent(context: Context, url: String, title: String) =
            Intent(context, PlayerActivity::class.java).putExtra(EXTRA_URL, url).putExtra(EXTRA_TITLE, title)
    }
}
