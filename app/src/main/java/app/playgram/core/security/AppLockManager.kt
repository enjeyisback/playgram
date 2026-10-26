package app.playgram.core.security

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AppLockManager(private val context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("playgram.tools", Context.MODE_PRIVATE)

    private val _isLocked = MutableStateFlow(false)
    val isLocked: StateFlow<Boolean> = _isLocked.asStateFlow()

    val isPinSet: Boolean
        get() = prefs.getString("security.pin", null) != null

    val isBiometricEnabled: Boolean
        get() = prefs.getBoolean("security.biometric", false)

    val isScreenshotBlocked: Boolean
        get() = prefs.getBoolean("secure.screenshots", false)

    init {
        if (isPinSet) {
            _isLocked.value = true
        }
    }

    fun setPin(pin: String) {
        prefs.edit().putString("security.pin", pin).apply()
        _isLocked.value = false
    }

    fun removePin() {
        prefs.edit().remove("security.pin").apply()
        _isLocked.value = false
    }

    fun verifyPin(pin: String): Boolean {
        val saved = prefs.getString("security.pin", null)
        val valid = (saved != null && saved == pin)
        if (valid) {
            _isLocked.value = false
        }
        return valid
    }

    fun unlockWithBiometric() {
        _isLocked.value = false
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("security.biometric", enabled).apply()
    }

    fun setScreenshotBlocked(blocked: Boolean, activity: Activity? = null) {
        prefs.edit().putBoolean("secure.screenshots", blocked).apply()
        applySecurityFlags(activity)
    }

    fun applySecurityFlags(activity: Activity?) {
        activity ?: return
        if (isScreenshotBlocked) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.setRecentsScreenshotEnabled(!isScreenshotBlocked)
        }
    }

    companion object {
        @Volatile
        private var INSTANCE: AppLockManager? = null

        fun getInstance(context: Context): AppLockManager {
            return INSTANCE ?: synchronized(this) {
                val inst = AppLockManager(context.applicationContext)
                INSTANCE = inst
                inst
            }
        }
    }
}
