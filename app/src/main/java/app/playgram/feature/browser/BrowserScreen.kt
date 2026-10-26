package app.playgram.feature.browser

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import app.playgram.PlaygramApp
import app.playgram.core.design.*
import app.playgram.feature.navigation.NavRoutes
import java.io.ByteArrayInputStream

/** Small built-in host list; matches by suffix. */
private val blockedHosts = listOf(
    "doubleclick.net", "googlesyndication.com", "googleadservices.com", "google-analytics.com",
    "googletagmanager.com", "adservice.google.com", "adnxs.com", "taboola.com", "outbrain.com",
    "popads.net", "propellerads.com", "exoclick.com", "juicyads.com", "adsterra.com",
    "scorecardresearch.com", "facebook.net", "hotjar.com", "mc.yandex.ru", "ads.pubmatic.com"
)

private fun isBlocked(host: String?): Boolean =
    host != null && blockedHosts.any { host == it || host.endsWith(".$it") }

private fun toTarget(input: String): String {
    val t = input.trim()
    return when {
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains(".") && !t.contains(" ") -> "https://$t"
        else -> "https://duckduckgo.com/?q=${java.net.URLEncoder.encode(t, "UTF-8")}"
    }
}

@Composable
fun BrowserScreen(
    initialUrl: String = "",
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    app.playgram.core.design.KinoScaled { BrowserScreenContent(initialUrl = initialUrl, onBack = onBack, onNavigate = onNavigate) }
}

@Composable
private fun BrowserScreenContent(
    initialUrl: String = "",
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val colors = LocalPlaygramColors.current
    val downloadManager = PlaygramApp.instance.downloadManager

    var showStart by remember { mutableStateOf(initialUrl.isBlank()) }
    var inputUrl by remember { mutableStateOf(initialUrl) }
    var detectedStreamUrl by remember { mutableStateOf<String?>(null) }
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var menuOpen by remember { mutableStateOf(false) }

    fun go(text: String) {
        if (text.isBlank()) return
        val target = toTarget(text)
        inputUrl = target
        showStart = false
        webViewInstance?.loadUrl(target)
    }

    BackHandler {
        val wv = webViewInstance
        when {
            !showStart && wv?.canGoBack() == true -> wv.goBack()
            !showStart -> { showStart = true; inputUrl = "" }
            else -> onBack()
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Address bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(colors.surfaceElevated)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Public, null, tint = colors.textSecondary)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (inputUrl.isEmpty()) {
                        Text("Search or type an address", fontSize = 17.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
                    }
                    BasicTextField(
                        value = inputUrl,
                        onValueChange = { inputUrl = it },
                        singleLine = true,
                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 17.sp),
                        cursorBrush = SolidColor(AccentBlueLight),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { go(inputUrl) }),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            IconButton(onClick = { if (showStart) go(inputUrl) else webViewInstance?.reload() }) {
                Icon(Icons.Outlined.Refresh, "Reload", tint = colors.textPrimary)
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.mediaPlaybackRequiresUserGesture = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val uri = request?.url
                                if (isBlocked(uri?.host)) {
                                    return WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                                }
                                val url = uri?.toString() ?: ""
                                if (url.contains(".m3u8") || url.contains(".mp4") || url.contains(".webm")) {
                                    detectedStreamUrl = url
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                if (url != null) inputUrl = url
                            }
                        }
                        webViewInstance = this
                        if (initialUrl.isNotBlank()) loadUrl(toTarget(initialUrl))
                    }
                },
                update = { it.visibility = if (showStart) android.view.View.INVISIBLE else android.view.View.VISIBLE },
                modifier = Modifier.fillMaxSize()
            )

            if (showStart) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .background(colors.background)
                        .padding(horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(Modifier.height(32.dp))
                    Box(
                        Modifier.size(90.dp).clip(CircleShape).background(AccentContainer),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.VideoLibrary, null, tint = AccentBlueLight, modifier = Modifier.size(42.dp)) }
                    Spacer(Modifier.height(20.dp))
                    Text("Playgram browser", fontSize = 32.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
                    Spacer(Modifier.height(24.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(colors.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 15.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Search, null, tint = AccentBlueLight)
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (inputUrl.isBlank()) "Search with DuckDuckGo or type an address" else inputUrl,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Light,
                            color = colors.textSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Row(Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp), verticalAlignment = Alignment.Top) {
                        Icon(Icons.Outlined.Shield, null, tint = colors.statusSuccess, modifier = Modifier.size(20.dp).padding(top = 2.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            "Common ad and tracker hosts (${blockedHosts.size} built in) are blocked on pages you open here.",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Light,
                            color = colors.textSecondary
                        )
                    }
                }
            }

            // Stream sniffer banner
            if (detectedStreamUrl != null && !showStart) {
                KinoCard(
                    container = colors.surfaceElevated,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(16.dp)
                        .border(1.dp, AccentBlue, RoundedCornerShape(22.dp))
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.OndemandVideo, null, tint = AccentBlueLight)
                        Spacer(Modifier.width(10.dp))
                        Text("Video stream found", fontSize = 18.sp, fontWeight = FontWeight.Light, color = colors.textPrimary, modifier = Modifier.weight(1f))
                        IconButton(onClick = { detectedStreamUrl = null }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Outlined.Close, "Dismiss", tint = colors.textSecondary)
                        }
                    }
                    Text(
                        detectedStreamUrl ?: "",
                        fontSize = 12.sp,
                        color = colors.textSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KinoButton(
                            "Stream", icon = Icons.Outlined.PlayArrow, modifier = Modifier.weight(1f),
                            onClick = {
                                val stream = detectedStreamUrl ?: return@KinoButton
                                onNavigate(NavRoutes.player(stream, "Web stream"))
                            }
                        )
                        KinoButton(
                            "Download", icon = Icons.Outlined.FileDownload, tonal = true, modifier = Modifier.weight(1f),
                            onClick = {
                                val stream = detectedStreamUrl ?: return@KinoButton
                                downloadManager.enqueueDownload(
                                    id = "web_${System.currentTimeMillis()}",
                                    title = "Web video",
                                    url = stream,
                                    totalBytes = 0L
                                )
                                detectedStreamUrl = null
                            }
                        )
                    }
                }
            }
        }

        // Bottom bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            val canBack = !showStart && webViewInstance?.canGoBack() == true
            val canFwd = !showStart && webViewInstance?.canGoForward() == true
            IconButton(onClick = { webViewInstance?.goBack() }, enabled = canBack) {
                Icon(Icons.Outlined.ArrowBack, "Back", tint = if (canBack) colors.textPrimary else colors.textTertiary)
            }
            IconButton(onClick = { webViewInstance?.goForward() }, enabled = canFwd) {
                Icon(Icons.Outlined.ArrowForward, "Forward", tint = if (canFwd) colors.textPrimary else colors.textTertiary)
            }
            IconButton(onClick = { showStart = true; inputUrl = "" }) {
                Icon(Icons.Outlined.Home, "Home", tint = colors.textPrimary)
            }
            Box(
                Modifier
                    .size(34.dp)
                    .border(1.5.dp, colors.textPrimary, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) { Text("1", fontSize = 15.sp, color = colors.textPrimary) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Outlined.MoreVert, "More", tint = colors.textPrimary)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text("Back to Playgram") }, onClick = { menuOpen = false; onBack() })
                    DropdownMenuItem(
                        text = { Text("Check for streams") },
                        onClick = {
                            menuOpen = false
                            webViewInstance?.url?.takeIf { it.contains(".m3u8") || it.contains(".mp4") }?.let { detectedStreamUrl = it }
                        }
                    )
                }
            }
        }
    }
}
