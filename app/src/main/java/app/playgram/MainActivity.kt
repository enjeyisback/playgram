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
            handleIncomingIntent(intent)
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

    private fun handleIncomingIntent(intent: Intent?) {
        intent ?: return
    }
}
