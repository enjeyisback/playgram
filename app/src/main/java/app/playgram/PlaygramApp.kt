package app.playgram

import android.app.Application
import android.util.Log
import app.playgram.core.database.KinoDatabase
import app.playgram.core.download.DownloadManager
import app.playgram.core.security.AppLockManager
import app.playgram.core.telegram.TelegramManager

class PlaygramApp : Application() {

    lateinit var database: KinoDatabase
        private set

    lateinit var downloadManager: DownloadManager
        private set

    lateinit var telegramManager: TelegramManager
        private set

    lateinit var appLockManager: AppLockManager
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("PlaygramCrash", "Uncaught exception in thread ${thread.name}", throwable)
            try {
                val crashFile = java.io.File(filesDir, "crash.log")
                crashFile.writeText(throwable.stackTraceToString())
            } catch (_: Exception) {}
        }

        try {
            database = KinoDatabase.getInstance(this)
            downloadManager = DownloadManager.getInstance(this)
            telegramManager = TelegramManager.getInstance(this)
            appLockManager = AppLockManager.getInstance(this)
        } catch (e: Throwable) {
            Log.e("PlaygramApp", "Error during subsystem initialization", e)
        }
    }

    companion object {
        lateinit var instance: PlaygramApp
            private set
    }
}
