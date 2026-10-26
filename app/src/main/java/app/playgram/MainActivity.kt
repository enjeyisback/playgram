package app.playgram

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import app.playgram.core.design.PlaygramTheme
import app.playgram.core.security.AppLockManager
import app.playgram.feature.navigation.PlaygramNavigation
import app.playgram.feature.security.AppLockScreen

class MainActivity : ComponentActivity() {

    private var appLockManager: AppLockManager? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            appLockManager = PlaygramApp.instance.appLockManager
            appLockManager?.applySecurityFlags(this)
        } catch (e: Throwable) {
            Log.e("MainActivity", "Failed to access appLockManager", e)
        }

        try {
            // A recreated activity gets the original intent again; don't replay a share/link from history.
            if (savedInstanceState == null && intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) handleIncomingIntent(intent)
        } catch (e: Throwable) {
            Log.e("MainActivity", "Error handling intent", e)
        }

        app.playgram.core.design.AccentPrefs.load(this)
        app.playgram.core.update.UpdateManager.getInstance(this).checkIfDue()
        setContent {
            val lockManager = appLockManager
            val isLocked = lockManager?.isLocked?.collectAsState()?.value ?: false

            var showSplash by rememberSaveable { mutableStateOf(true) }
            PlaygramTheme(accent = app.playgram.core.design.AccentPrefs.current) {
                val signedIn = PlaygramApp.instance.telegramManager.signedIn.collectAsState().value
                if (!signedIn) {
                    app.playgram.feature.auth.LoginScreen()
                } else if (isLocked) {
                    AppLockScreen(onUnlocked = {
                        // Unlocked
                    })
                } else {
                    PlaygramNavigation()
                }
                if (signedIn && !isLocked) app.playgram.feature.update.UpdatePrompt()
                if (showSplash) {
                    app.playgram.core.design.PlaygramSplash(onFinished = { showSplash = false })
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        try {
            handleIncomingIntent(intent)
        } catch (e: Throwable) {
            Log.e("MainActivity", "Error in onNewIntent", e)
        }
    }

    override fun onResume() {
        super.onResume()
        try {
            appLockManager?.applySecurityFlags(this)
        } catch (_: Throwable) {}
    }

    /** Home button / recents while a video plays: float it instead of stopping it. */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O &&
            app.playgram.core.player.PipState.active && app.playgram.core.player.PipState.playing &&
            app.playgram.core.player.PipState.enabledInSettings(this) && !isInPictureInPictureMode
        ) {
            try { enterPictureInPictureMode(app.playgram.core.player.PipState.params(false)) } catch (e: Throwable) { Log.e("MainActivity", "PiP failed", e) }
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        app.playgram.core.player.PipState.inPip = isInPictureInPictureMode
    }

    override fun onStop() {
        super.onStop()
        // The floating window was closed (a dismissed window stops the activity while still in PiP mode).
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N && isInPictureInPictureMode) {
            app.playgram.core.player.PipState.pause?.invoke()
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        Incoming.from(this, intent)?.let { Incoming.pending.value = it }
    }
}
