package app.playgram.core.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import app.playgram.BuildConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** A newer release found on GitHub. */
data class UpdateInfo(val version: String, val notes: String, val apkUrl: String, val sizeBytes: Long)

sealed class UpdateState {
    object Idle : UpdateState()
    object Checking : UpdateState()
    object UpToDate : UpdateState()
    data class Available(val info: UpdateInfo) : UpdateState()
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState()
    data class Failed(val message: String) : UpdateState()
}

/**
 * Looks for a newer release in the GitHub repo set as UPDATE_REPO in local.properties
 * ("owner/name"), downloads its .apk and hands it to Android's installer.
 * The release tag is the version ("v1.1.0" or "1.1.0"); the first .apk asset is the update.
 */
class UpdateManager private constructor(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val prefs = context.getSharedPreferences("playgram_update", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** "Later" hides the dialog until the next app start; Settings still shows the update. */
    private val _dialogDismissed = MutableStateFlow(false)
    val dialogDismissed: StateFlow<Boolean> = _dialogDismissed.asStateFlow()
    fun dismissDialog() { _dialogDismissed.value = true }

    val enabled: Boolean get() = BuildConfig.UPDATE_REPO.contains('/')

    /** Automatic check on app start, at most every 6 hours. */
    fun checkIfDue() {
        if (!enabled) return
        val last = prefs.getLong("last_check", 0L)
        if (System.currentTimeMillis() - last < 6 * 3600_000L) return
        check()
    }

    fun check() {
        if (!enabled || _state.value is UpdateState.Checking || _state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Checking
        scope.launch {
            _state.value = try {
                val info = fetchLatest()
                prefs.edit().putLong("last_check", System.currentTimeMillis()).apply()
                if (info != null && isNewer(info.version, BuildConfig.VERSION_NAME)) UpdateState.Available(info) else UpdateState.UpToDate
            } catch (e: Exception) {
                UpdateState.Failed("Couldn't check for updates. Check your connection and try again.")
            }
        }
    }

    private fun fetchLatest(): UpdateInfo? {
        val conn = URL("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest").openConnection() as HttpURLConnection
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "Playgram/${BuildConfig.VERSION_NAME}")
        conn.connectTimeout = 15_000; conn.readTimeout = 15_000
        if (conn.responseCode == 404) return null
        val json = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        val assets = json.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.getString("name").endsWith(".apk", ignoreCase = true)) {
                return UpdateInfo(
                    version = json.getString("tag_name").trim().removePrefix("v").removePrefix("V"),
                    notes = json.optString("body", "").trim(),
                    apkUrl = a.getString("browser_download_url"),
                    sizeBytes = a.optLong("size", 0L)
                )
            }
        }
        return null
    }

    fun download(info: UpdateInfo) {
        if (_state.value is UpdateState.Downloading) return
        _state.value = UpdateState.Downloading(info, 0f)
        scope.launch {
            try {
                val dir = File(context.cacheDir, "updates").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
                val file = File(dir, "Playgram-${info.version}.apk")
                val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
                conn.setRequestProperty("User-Agent", "Playgram/${BuildConfig.VERSION_NAME}")
                conn.connectTimeout = 15_000; conn.readTimeout = 30_000
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: info.sizeBytes
                conn.inputStream.use { input ->
                    file.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastEmit = 0L
                        while (true) {
                            val n = input.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            val now = System.currentTimeMillis()
                            if (total > 0 && now - lastEmit > 200) {
                                lastEmit = now
                                _state.value = UpdateState.Downloading(info, (done.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
                _state.value = UpdateState.Available(info)
                install(file)
            } catch (e: Exception) {
                _state.value = UpdateState.Failed("Download failed. Try again.")
            }
        }
    }

    /** Opens Android's installer for the downloaded APK; asks for the "install unknown apps" permission first if needed. */
    private fun install(apk: File) {
        if (!context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            // Back from that screen, "Update now" installs the file that is already downloaded.
            return
        }
        val uri = FileProvider.getUriForFile(context, "app.playgram.downloads.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    /** Installs an already downloaded update (after the permission was granted), or downloads it again. */
    fun installDownloaded(info: UpdateInfo) {
        val file = File(File(context.cacheDir, "updates"), "Playgram-${info.version}.apk")
        if (file.exists() && file.length() > 0 && (info.sizeBytes <= 0 || file.length() == info.sizeBytes)) install(file) else download(info)
    }

    companion object {
        @Volatile private var instance: UpdateManager? = null
        fun getInstance(context: Context): UpdateManager =
            instance ?: synchronized(this) { instance ?: UpdateManager(context.applicationContext).also { instance = it } }

        /** "1.10.0" is newer than "1.9.2": compares the dotted numbers one by one. */
        fun isNewer(candidate: String, current: String): Boolean {
            val a = candidate.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            val b = current.split('.').map { it.takeWhile(Char::isDigit).toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(a.size, b.size)) {
                val x = a.getOrElse(i) { 0 }
                val y = b.getOrElse(i) { 0 }
                if (x != y) return x > y
            }
            return false
        }
    }
}
