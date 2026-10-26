package app.playgram.feature.tools

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.playgram.PlaygramApp
import app.playgram.core.design.*
import app.playgram.feature.auth.TelegramLoginDialog
import app.playgram.feature.navigation.NavRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private fun prefs(context: Context) = context.getSharedPreferences("playgram_settings", Context.MODE_PRIVATE)

@Composable
fun ToolsScreen(
    initialLink: String = "",
    onBack: (() -> Unit)?,
    onNavigate: (String) -> Unit
) {
    app.playgram.core.design.KinoScaled { ToolsScreenContent(initialLink = initialLink, onBack = onBack, onNavigate = onNavigate) }
}

@Composable
private fun ToolsScreenContent(
    initialLink: String = "",
    onBack: (() -> Unit)?,
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val colors = LocalPlaygramColors.current
    val appLockManager = PlaygramApp.instance.appLockManager
    val telegramManager = PlaygramApp.instance.telegramManager
    val scope = rememberCoroutineScope()

    val userProfile by telegramManager.userProfile.collectAsState()
    val isDemoMode by telegramManager.isDemoMode.collectAsState()
    var showLoginDialog by remember { mutableStateOf(false) }


    var isSecureScreen by remember { mutableStateOf(appLockManager.isScreenshotBlocked) }
    var hasPinSet by remember { mutableStateOf(appLockManager.isPinSet) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showRemovePin by remember { mutableStateOf(false) }

    val p = remember { prefs(context) }
    var pictureSize by remember { mutableStateOf(p.getString("picture_size", "Fit") ?: "Fit") }
    var rememberedPlayer by remember { mutableStateOf(app.playgram.core.player.ExternalPlayers.rememberedLabel(context)) }
    val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) rememberedPlayer = app.playgram.core.player.ExternalPlayers.rememberedLabel(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    var defaultPlayer by remember { mutableStateOf(app.playgram.core.player.ExternalPlayers.chosenName(context)) }
    var autoPlay by remember { mutableStateOf(p.getBoolean("autoplay", true)) }
    var keepOn by remember { mutableStateOf(p.getBoolean("keep_screen_on", true)) }
    var pipHome by remember { mutableStateOf(p.getBoolean("pip_home", true)) }
    var pipBack by remember { mutableStateOf(p.getBoolean("pip_back", false)) }
    var query by remember { mutableStateOf("") }
    var cacheBytes by remember { mutableStateOf<Long?>(null) }
    var clearing by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    fun refreshCache() = telegramManager.cacheSize { cacheBytes = it }
    LaunchedEffect(Unit) { refreshCache() }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        KinoTopBar(title = "Settings", onBack = onBack)

        if (confirmClear) {
            AlertDialog(
                onDismissRequest = { confirmClear = false },
                title = { Text("Clear streaming cache?", fontWeight = FontWeight.Light, color = colors.textPrimary) },
                text = {
                    Text(
                        "Deletes ${cacheBytes?.let { formatBytes(it) } ?: "the"} of streamed videos from this phone. They stay on Telegram and stream again when you play them. Downloads are not touched.",
                        color = colors.textSecondary
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmClear = false
                        clearing = true
                        telegramManager.clearMediaCache { clearing = false; refreshCache() }
                    }) { Text("Clear", color = colors.statusError) }
                },
                dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel", color = AccentBlueLight) } },
                containerColor = colors.surfaceVariant
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp)
        ) {
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(18.dp))
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Search, null, tint = colors.textSecondary)
                    Spacer(Modifier.width(14.dp))
                    Box(Modifier.weight(1f)) {
                        if (query.isEmpty()) Text("Search settings", fontSize = 17.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            singleLine = true,
                            textStyle = TextStyle(color = colors.textPrimary, fontSize = 17.sp),
                            cursorBrush = SolidColor(AccentBlueLight),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // ---- Account ----
            item {
                SettingsSection(Icons.Outlined.AccountCircle, "Account")
                if (userProfile != null) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                        Box(
                            Modifier.size(48.dp).clip(CircleShape).background(Color(0xFF4DA3F5)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (userProfile?.firstName ?: "U").take(1).uppercase(),
                                fontSize = 20.sp, fontWeight = FontWeight.Light, color = Color(0xFF06223F)
                            )
                            userProfile?.avatarUrl?.substringAfterLast('/')?.toIntOrNull()?.let {
                                app.playgram.feature.chats.TgImage(it, Modifier.fillMaxSize())
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(userProfile?.firstName ?: "User", fontSize = 18.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
                            Text(userProfile?.phoneNumber ?: "", fontSize = 15.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
                        }
                    }
                    SettingRow(
                        "Session · ${if (isDemoMode) "Sample data" else "Connected"}",
                        "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} · Android ${android.os.Build.VERSION.RELEASE} · Playgram ${app.playgram.BuildConfig.VERSION_NAME}"
                    )
                    SettingSwitch(
                        "Use sample data",
                        "Show offline sample chats instead of your real Telegram chats",
                        isDemoMode
                    ) { telegramManager.toggleDemoMode(it) }
                    SettingRow(
                        "Log out",
                        "Removes this account from Playgram; saved files stay",
                        titleColor = colors.statusError,
                        onClick = { telegramManager.logOut() }
                    ) { Icon(Icons.Outlined.ChevronRight, null, tint = colors.textSecondary) }
                } else {
                    SettingRow(
                        "Not connected",
                        "Sample data is showing. Log in to see your own channels, groups and cloud media."
                    )
                    KinoButton(
                        "Connect Telegram account",
                        onClick = { telegramManager.startLogin(); showLoginDialog = true },
                        icon = Icons.Outlined.Login,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }
            }

            // ---- Storage ----
            item {
                SettingsSection(Icons.Outlined.Storage, "Storage")
                SettingRow(
                    "Streaming cache · ${cacheBytes?.let { formatBytes(it) } ?: "—"}",
                    "Videos Telegram keeps on this phone after you stream them, at most 5 GB (oldest go first). Files saved to Downloads are separate and stay."
                ) {
                    KinoButton(if (clearing) "Clearing…" else "Clear", tonal = true, onClick = { if (!clearing) confirmClear = true })
                }
            }

            // ---- Playback ----
            item {
                SettingsSection(Icons.Outlined.PlayCircleOutline, "Playback")
                Text("VIDEO", fontSize = 14.sp, fontWeight = FontWeight.Light, color = colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
                Text("Default player", fontSize = 18.sp, fontWeight = FontWeight.Light, color = colors.textPrimary, modifier = Modifier.padding(top = 8.dp))
                Text("Every video opens in this player. Only installed players are listed.", fontSize = 14.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf(app.playgram.core.player.ExternalPlayers.BUILT_IN) +
                        app.playgram.core.player.ExternalPlayers.all.filter { it.packages.isEmpty() || app.playgram.core.player.ExternalPlayers.installedPackage(context, it) != null }.map { it.name }
                    options.forEach { opt ->
                        KinoPill(opt, defaultPlayer == opt, {
                            defaultPlayer = opt
                            p.edit().putString(app.playgram.core.player.ExternalPlayers.PREF_KEY, opt).apply()
                            // Choosing "System default" starts over: the next video asks, and the pick is remembered.
                            app.playgram.core.player.ExternalPlayers.forgetRemembered(context)
                            rememberedPlayer = null
                        })
                    }
                }
                if (defaultPlayer == "System default") {
                    Text(
                        if (rememberedPlayer != null) "Videos open in $rememberedPlayer. Android asks again only if you reset."
                        else "Android asks once; the player you pick is remembered from then on.",
                        fontSize = 14.sp, fontWeight = FontWeight.Light, color = colors.textSecondary
                    )
                    if (rememberedPlayer != null) TextButton(onClick = {
                        app.playgram.core.player.ExternalPlayers.forgetRemembered(context); rememberedPlayer = null
                    }) { Text("Ask again", color = AccentBlueLight) }
                }
                Text("Default picture size", fontSize = 18.sp, fontWeight = FontWeight.Light, color = colors.textPrimary, modifier = Modifier.padding(top = 8.dp))
                Text("Whole picture, letterboxed · each file remembers its own", fontSize = 14.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
                Row(
                    Modifier.horizontalScroll(rememberScrollState()).padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Fit", "Fill", "Stretch", "100%", "Zoom", "Crop 16:9", "Crop 4:3").forEach { opt ->
                        KinoPill(opt, pictureSize == opt, { pictureSize = opt; p.edit().putString("picture_size", opt).apply() })
                    }
                }
                SettingSwitch("Auto-play next video", "Count down at the end and play the next video in the chat", autoPlay) {
                    autoPlay = it; p.edit().putBoolean("autoplay", it).apply()
                }
                SettingSwitch("Keep screen on", "While a video plays", keepOn) {
                    keepOn = it; p.edit().putBoolean("keep_screen_on", it).apply()
                }
                SettingSwitch("Picture-in-picture on Home", "A floating window when you leave a playing video", pipHome) {
                    pipHome = it; p.edit().putBoolean("pip_home", it).apply()
                }
                SettingSwitch("Back button enters picture-in-picture", "Off: Back leaves the player", pipBack) {
                    pipBack = it; p.edit().putBoolean("pip_back", it).apply()
                }
            }

            // ---- Security ----
            item {
                SettingsSection(Icons.Outlined.Lock, "Privacy & security")
                SettingSwitch("Block screenshots", "Hides previews in recent apps and blocks screen capture", isSecureScreen) {
                    isSecureScreen = it
                    appLockManager.setScreenshotBlocked(it, activity)
                }
                SettingRow(
                    "App lock PIN",
                    if (hasPinSet) "On · Playgram asks for the PIN when it opens" else "Off · set a 4-digit PIN to lock Playgram",
                    onClick = { showPinDialog = true }
                ) {
                    if (hasPinSet) KinoButton("Remove", tonal = true, onClick = { showRemovePin = true })
                    else Icon(Icons.Outlined.ChevronRight, null, tint = colors.textSecondary)
                }
                if (hasPinSet) SettingRow("Change PIN", "Enter your current PIN, then a new one", onClick = { showPinDialog = true }) {
                    Icon(Icons.Outlined.ChevronRight, null, tint = colors.textSecondary)
                }
            }

            // ---- Updates ----
            item {
                val um = remember { app.playgram.core.update.UpdateManager.getInstance(context) }
                val updateState by um.state.collectAsState()
                SettingsSection(Icons.Outlined.SystemUpdate, "Updates")
                val s = updateState
                val subtitle = when {
                    !um.enabled -> "Updates aren't set up in this build."
                    s is app.playgram.core.update.UpdateState.Checking -> "Checking…"
                    s is app.playgram.core.update.UpdateState.UpToDate -> "You're on the latest version."
                    s is app.playgram.core.update.UpdateState.Available -> "Version ${s.info.version} is ready to install."
                    s is app.playgram.core.update.UpdateState.Downloading -> "Downloading ${(s.progress * 100).toInt()}%"
                    s is app.playgram.core.update.UpdateState.Failed -> s.message
                    else -> "Checked automatically when the app opens."
                }
                SettingRow("Playgram ${app.playgram.BuildConfig.VERSION_NAME}", subtitle) {
                    if (um.enabled) {
                        if (s is app.playgram.core.update.UpdateState.Available) KinoButton("Update", onClick = { um.installDownloaded(s.info) })
                        else if (s !is app.playgram.core.update.UpdateState.Downloading && s !is app.playgram.core.update.UpdateState.Checking)
                            KinoButton("Check", tonal = true, onClick = { um.check() })
                    }
                }
            }

            // ---- Accent ----
            item {
                SettingsSection(Icons.Outlined.Palette, "Accent colour")
                Text(
                    "${AccentPrefs.current.title} · buttons, icons and highlights",
                    fontSize = 14.sp, fontWeight = FontWeight.Light, color = colors.textSecondary
                )
                Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    PlaygramAccent.values().forEach { accent ->
                        Box(
                            Modifier.size(42.dp).clip(CircleShape).background(accent.color)
                                .clickable { AccentPrefs.set(context, accent) },
                            contentAlignment = Alignment.Center
                        ) {
                            if (accent == AccentPrefs.current) Icon(Icons.Outlined.Check, "${accent.title} selected", tint = Color.White)
                        }
                    }
                }
            }
        }
    }

    if (showPinDialog || showRemovePin) {
        val removing = showRemovePin
        val needsCurrent = hasPinSet
        var currentPin by remember { mutableStateOf("") }
        var newPin by remember { mutableStateOf("") }
        var pinError by remember { mutableStateOf<String?>(null) }
        fun close() { showPinDialog = false; showRemovePin = false }
        val pinField = @Composable { value: String, label: String, onChange: (String) -> Unit ->
            OutlinedTextField(
                value = value,
                onValueChange = { v -> if (v.length <= 4 && v.all { it.isDigit() }) { onChange(v); pinError = null } },
                label = { Text(label) },
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                singleLine = true,
                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
            )
        }
        AlertDialog(
            onDismissRequest = { close() },
            title = {
                Text(
                    when { removing -> "Remove app lock PIN"; needsCurrent -> "Change PIN"; else -> "Set app lock PIN" },
                    fontWeight = FontWeight.Light, color = colors.textPrimary
                )
            },
            text = {
                Column {
                    if (needsCurrent) pinField(currentPin, "Current PIN") { currentPin = it }
                    if (!removing) pinField(newPin, if (needsCurrent) "New 4-digit PIN" else "4-digit PIN") { newPin = it }
                    pinError?.let { Text(it, fontSize = 13.sp, color = colors.statusError, modifier = Modifier.padding(top = 6.dp)) }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        when {
                            needsCurrent && !appLockManager.verifyPin(currentPin) -> pinError = "That isn't your current PIN"
                            removing -> { appLockManager.removePin(); hasPinSet = false; close() }
                            newPin.length != 4 -> pinError = "Enter 4 digits"
                            else -> { appLockManager.setPin(newPin); hasPinSet = true; close() }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = if (removing) colors.statusError else AccentBlue)
                ) { Text(if (removing) "Remove" else "Save") }
            },
            dismissButton = {
                TextButton(onClick = { close() }) { Text("Cancel", color = colors.textSecondary) }
            },
            containerColor = colors.surfaceVariant
        )
    }

    if (showLoginDialog) {
        TelegramLoginDialog(
            onDismiss = { showLoginDialog = false },
            onLoginSuccess = { showLoginDialog = false }
        )
    }
}

private fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format("%.1f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format("%.0f MB", b / (1L shl 20).toDouble())
    else -> String.format("%.0f KB", b / 1024.0)
}
