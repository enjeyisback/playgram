package app.playgram.feature.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.util.Rational
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.automirrored.outlined.PlaylistAdd
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import app.playgram.PlaygramApp
import app.playgram.core.design.*
import kotlinx.coroutines.delay

private val pictureModes = listOf("Fit", "Fill", "Zoom")

private typealias ExternalPlayer = app.playgram.core.player.ExternalPlayer
private val externalPlayers = app.playgram.core.player.ExternalPlayers.all
private fun installedPackage(context: Context, player: ExternalPlayer) = app.playgram.core.player.ExternalPlayers.installedPackage(context, player)

private fun videoCodecName(mime: String?): String? = when {
    mime == null -> null
    mime.endsWith("avc") -> "H.264"
    mime.endsWith("hevc") -> "H.265"
    mime.endsWith("av01") -> "AV1"
    mime.contains("vp9") -> "VP9"
    mime.contains("vp8") -> "VP8"
    mime.contains("mp4v") || mime.endsWith("mpeg4") -> "MPEG-4"
    else -> null
}

private fun audioCodecName(mime: String?): String? = when {
    mime == null -> null
    mime.contains("mp4a") -> "AAC"
    mime.endsWith("eac3") || mime.endsWith("eac3-joc") -> "E-AC3"
    mime.endsWith("ac3") -> "AC3"
    mime.contains("opus") -> "Opus"
    mime.contains("vorbis") -> "Vorbis"
    mime.endsWith("mpeg") || mime.endsWith("mpeg-L2") -> "MP3"
    mime.contains("flac") -> "FLAC"
    mime.contains("dts") -> "DTS"
    else -> null
}

private fun formatSize(b: Long): String = when {
    b >= 1L shl 30 -> String.format("%.2f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format("%.0f MB", b / (1L shl 20).toDouble())
    else -> String.format("%.0f KB", b / 1024.0)
}

private fun fmt(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    else String.format("%d:%02d", s / 60, s % 60)
}

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    videoUrl: String,
    videoTitle: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    // Settings -> Default player: hand the video straight to VLC / MX Player / ... and come back here.
    val external = remember { app.playgram.core.player.ExternalPlayers.defaultExternal(context) }
    var handoffFailed by remember { mutableStateOf(false) }
    if (external != null && !handoffFailed) {
        ExternalHandoff(external, videoUrl, videoTitle, onBack, onFailed = { handoffFailed = true })
    } else {
        app.playgram.core.design.KinoScaled { PlayerScreenContent(videoUrl = videoUrl, videoTitle = videoTitle, onBack = onBack) }
    }
}

@Composable
private fun ExternalHandoff(player: ExternalPlayer, videoUrl: String, videoTitle: String, onBack: () -> Unit, onFailed: () -> Unit) {
    val context = LocalContext.current
    val pg = PlaygramApp.instance
    LaunchedEffect(Unit) {
        val playUrl = pg.telegramManager.withNameHint(videoUrl, videoTitle)
        val key = Uri.parse(videoUrl).let { u ->
            val chat = u.getQueryParameter("chat"); val msg = u.getQueryParameter("msg")
            if (videoUrl.startsWith("tg://") && chat != null && msg != null) "tg://message/$chat/$msg" else videoUrl
        }
        val resumeAt = pg.database.watchPositionDao().getWatchPosition(key)?.takeIf { !it.completed }?.positionMs ?: 0L
        try {
            val intent = app.playgram.core.player.ExternalPlayers.viewIntent(context, videoUrl, playUrl, videoTitle, resumeAt, installedPackage(context, player))
            context.startActivity(intent)
            // The other player keeps pulling the stream, so the file must stay until it is cleaned up later.
            if (videoUrl.startsWith("tg://")) pg.telegramManager.finishStream(playUrl, 2, pg.downloadManager.busyTelegramFileIds())
            onBack()
        } catch (e: Exception) {
            android.util.Log.e("PlaygramPlayer", "Hand-off to ${player.name} failed", e)
            onFailed()
        }
    }
    Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = Color.White, modifier = Modifier.size(36.dp))
    }
}

@OptIn(UnstableApi::class)
@Composable
private fun PlayerScreenContent(
    videoUrl: String,
    videoTitle: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val colors = LocalPlaygramColors.current
    val downloadManager = PlaygramApp.instance.downloadManager
    val prefs = remember { context.getSharedPreferences("playgram_settings", Context.MODE_PRIVATE) }

    // Older history links have no message ids; the file name lets the data source find the file again.
    val playUrl = remember(videoUrl) { PlaygramApp.instance.telegramManager.withNameHint(videoUrl, videoTitle) }
    val watchKey = remember(videoUrl) {
        val u = Uri.parse(videoUrl)
        val chat = u.getQueryParameter("chat"); val msg = u.getQueryParameter("msg")
        if (videoUrl.startsWith("tg://") && chat != null && msg != null) "tg://message/$chat/$msg" else videoUrl
    }
    val streamSession = remember { app.playgram.core.telegram.StreamSession() }
    val exoPlayer = remember {
        val builder = ExoPlayer.Builder(context)
        if (videoUrl.startsWith("tg://")) {
            builder.setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(context)
                    .setDataSourceFactory(app.playgram.core.telegram.TdFileDataSource.Factory(PlaygramApp.instance.telegramManager, streamSession))
            )
        }
        builder.build().apply {
            setMediaItem(MediaItem.fromUri(if (videoUrl.startsWith("/")) Uri.fromFile(java.io.File(videoUrl)) else Uri.parse(playUrl)))
            prepare()
            playWhenReady = true
        }
    }

    var isPlaying by remember { mutableStateOf(true) }
    var currentPosition by remember { mutableStateOf(0L) }
    var duration by remember { mutableStateOf(1L) }
    var controlsVisible by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableStateOf(1.0f) }
    var showTrackSheet by remember { mutableStateOf(false) }
    var fullscreen by remember { mutableStateOf(false) }
    var pictureMode by remember { mutableStateOf(pictureModes.indexOf(prefs.getString("picture_size", "Fit")).coerceAtLeast(0)) }
    // Streams are not kept unless asked: 0 discard, 1 seven days, 2 keep, 3 save to Downloads.
    var keepMode by remember { mutableStateOf(0) }
    var bitrate by remember { mutableStateOf("—") }
    var resolution by remember { mutableStateOf("") }
    var savedMsg by remember { mutableStateOf<String?>(if (videoUrl.startsWith("/")) "Playing the saved file." else null) }
    var videoCodec by remember { mutableStateOf<String?>(null) }
    var audioCodec by remember { mutableStateOf<String?>(null) }
    var fileSize by remember { mutableStateOf(if (videoUrl.startsWith("/")) java.io.File(videoUrl).length() else 0L) }
    val channelName = remember(videoUrl) {
        Uri.parse(videoUrl).getQueryParameter("chat")?.toLongOrNull()?.let { id -> PlaygramApp.instance.telegramManager.chats.value.firstOrNull { it.id == id }?.title }
    }
    LaunchedEffect(playUrl) {
        if (videoUrl.startsWith("tg://")) PlaygramApp.instance.telegramManager.resolveFile(Uri.parse(playUrl)) { f ->
            f?.let { fileSize = maxOf(it.size, it.expectedSize) }
        }
    }
    var showPlayerPicker by remember { mutableStateOf(false) }
    // Set once another player streams this video; closing Playgram's player must not cut it off.
    var handedOff by remember { mutableStateOf(false) }
    var buffering by remember { mutableStateOf(true) }
    var playError by remember { mutableStateOf<String?>(null) }
    val isLocalFile = videoUrl.startsWith("/")

    val watchDao = remember { PlaygramApp.instance.database.watchPositionDao() }
    fun saveProgress() {
        val dur = exoPlayer.duration
        val pos = exoPlayer.currentPosition
        if (dur <= 0 || pos < 5_000) return
        val done = pos >= dur - 30_000
        kotlinx.coroutines.runBlocking {
            watchDao.saveWatchPosition(app.playgram.core.database.WatchPositionEntity(watchKey, videoTitle, if (done) 0L else pos, dur, System.currentTimeMillis(), done, videoUrl))
        }
    }
    DisposableEffect(exoPlayer) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                android.util.Log.e("PlaygramPlayer", "Playback failed", error)
                playError = when (error.cause) {
                    is androidx.media3.exoplayer.source.UnrecognizedInputFormatException -> "The file's format isn't supported, or the file is damaged."
                    else -> error.cause?.message ?: error.errorCodeName
                }
                buffering = false
            }
            override fun onPlaybackStateChanged(state: Int) {
                buffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) playError = null
            if (state == Player.STATE_READY) duration = exoPlayer.duration.coerceAtLeast(1L)
            }
            override fun onVideoSizeChanged(size: androidx.media3.common.VideoSize) {
                if (size.height > 0) resolution = "${size.height}p"
                if (size.width > 0 && size.height > 0) app.playgram.core.player.PipState.ratio = size.width * size.pixelWidthHeightRatio / size.height
            }
            override fun onTracksChanged(tracks: androidx.media3.common.Tracks) {
                videoCodec = videoCodecName(exoPlayer.videoFormat?.sampleMimeType)
                audioCodec = audioCodecName(exoPlayer.audioFormat?.sampleMimeType)
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            saveProgress()
            streamSession.closed = true
            exoPlayer.release()
            if (keepMode == 3) downloadManager.enqueueDownload(id = "player_${System.currentTimeMillis()}", title = videoTitle, url = playUrl, totalBytes = 0L)
            PlaygramApp.instance.telegramManager.finishStream(playUrl, if (handedOff) 2 else keepMode, downloadManager.busyTelegramFileIds())
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    LaunchedEffect(exoPlayer) {
        val saved = watchDao.getWatchPosition(watchKey)
        if (saved != null && !saved.completed && saved.positionMs > 5_000) exoPlayer.seekTo(saved.positionMs)
        var ticks = 0
        while (true) {
            if (exoPlayer.isPlaying) saveProgress()
            if (++ticks % 120 == 0) PlaygramApp.instance.telegramManager.enforceCacheLimit()
            currentPosition = exoPlayer.currentPosition
            exoPlayer.videoFormat?.let { videoCodec = videoCodecName(it.sampleMimeType) ?: videoCodecName(it.codecs?.let { c -> if (c.startsWith("avc")) "video/avc" else if (c.startsWith("hev") || c.startsWith("hvc")) "video/hevc" else null }) }
            exoPlayer.audioFormat?.let { audioCodec = audioCodecName(it.sampleMimeType) }
            duration = exoPlayer.duration.coerceAtLeast(1L)
            exoPlayer.videoFormat?.bitrate?.takeIf { it > 0 }?.let { bitrate = String.format("%.1f Mb/s", it / 1_000_000.0) }
            delay(500)
        }
    }
    LaunchedEffect(controlsVisible, isPlaying) {
        if (controlsVisible && isPlaying) { delay(4000); controlsVisible = false }
    }

    // Keep the screen awake while the video plays or buffers (Settings -> Keep screen on).
    val view = androidx.compose.ui.platform.LocalView.current
    val keepOnPref = remember { prefs.getBoolean("keep_screen_on", true) }
    val awake = keepOnPref && (isPlaying || buffering)
    DisposableEffect(awake) {
        view.keepScreenOn = awake
        onDispose { view.keepScreenOn = false }
    }

    // Home button while playing -> floating window (MainActivity.onUserLeaveHint, and auto-enter on Android 12+).
    val pip = app.playgram.core.player.PipState
    LaunchedEffect(isPlaying, buffering) {
        pip.playing = isPlaying || buffering
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try { activity?.setPictureInPictureParams(pip.params(pip.playing && pip.enabledInSettings(context))) } catch (_: Exception) {}
        }
    }
    DisposableEffect(exoPlayer) {
        pip.active = true
        pip.pause = { exoPlayer.pause() }
        onDispose {
            pip.active = false; pip.playing = false; pip.pause = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                try { activity?.setPictureInPictureParams(pip.params(false)) } catch (_: Exception) {}
            }
        }
    }
    val inPip = pip.inPip

    // Swipe up/down on the right half = volume, on the left half = brightness.
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager }
    var gestureLabel by remember { mutableStateOf<String?>(null) }
    var gestureIsVolume by remember { mutableStateOf(true) }
    var gestureLevel by remember { mutableStateOf(0f) }
    LaunchedEffect(gestureLabel) { if (gestureLabel != null) { delay(900); gestureLabel = null } }
    DisposableEffect(Unit) {
        onDispose { activity?.window?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE } } }
    }
    fun windowBrightness(): Float {
        val set = activity?.window?.attributes?.screenBrightness ?: -1f
        if (set >= 0f) return set
        return try { android.provider.Settings.System.getInt(context.contentResolver, android.provider.Settings.System.SCREEN_BRIGHTNESS) / 255f } catch (_: Exception) { 0.5f }
    }

    fun setBars(hidden: Boolean) {
        val w = activity?.window ?: return
        val ctrl = androidx.core.view.WindowCompat.getInsetsController(w, view)
        if (hidden) {
            ctrl.systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            ctrl.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        } else ctrl.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            w.attributes = w.attributes.also {
                it.layoutInDisplayCutoutMode = if (hidden) android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                else android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
            }
        }
    }
    DisposableEffect(Unit) { onDispose { setBars(false) } }

    fun setFullscreen(on: Boolean) {
        fullscreen = on
        setBars(on)
        activity?.requestedOrientation =
            if (on) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE else ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
    androidx.activity.compose.BackHandler(enabled = fullscreen) { setFullscreen(false) }

    fun save() {
        downloadManager.enqueueDownload(
            id = "player_${System.currentTimeMillis()}",
            title = videoTitle, url = playUrl, totalBytes = 0L
        )
        savedMsg = "Added to Downloads."
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Video surface
        Box(
            modifier = (if (fullscreen || inPip) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f))
                .background(Color.Black)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                    controlsVisible = !controlsVisible
                }
                .pointerInput(Unit) {
                    var left = true
                    var level = 0f
                    detectVerticalDragGestures(
                        onDragStart = { start ->
                            left = start.x < size.width / 2f
                            level = if (left) windowBrightness()
                            else audioManager.getStreamVolume(android.media.AudioManager.STREAM_MUSIC).toFloat() / audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                        },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            level = (level - dy / (size.height * 0.8f)).coerceIn(0f, 1f)
                            gestureIsVolume = !left
                            gestureLevel = level
                            if (left) {
                                activity?.window?.let { w -> w.attributes = w.attributes.also { it.screenBrightness = level.coerceAtLeast(0.02f) } }
                            } else {
                                val max = audioManager.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
                                audioManager.setStreamVolume(android.media.AudioManager.STREAM_MUSIC, Math.round(level * max), 0)
                            }
                            gestureLabel = "${(level * 100).toInt()}%"
                        }
                    )
                }
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                    }
                },
                update = {
                    it.resizeMode = when (pictureMode) {
                        1 -> AspectRatioFrameLayout.RESIZE_MODE_FILL
                        2 -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                        else -> AspectRatioFrameLayout.RESIZE_MODE_FIT
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            gestureLabel?.let { label ->
                Row(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 24.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(Color(0xB3000000))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (gestureIsVolume) (if (gestureLevel <= 0f) Icons.Outlined.VolumeOff else Icons.Outlined.VolumeUp) else Icons.Outlined.LightMode,
                        null, tint = Color.White, modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    LinearProgressIndicator(progress = { gestureLevel }, modifier = Modifier.width(90.dp).height(4.dp), color = AccentBlueLight, trackColor = Color(0x55FFFFFF))
                    Spacer(Modifier.width(10.dp))
                    Text(label, fontFamily = JetBrainsMono, fontSize = 13.sp, color = Color.White)
                }
            }

            if (buffering && playError == null) {
                CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).size(44.dp))
            }
            playError?.let { msg ->
                Column(
                    Modifier.align(Alignment.Center).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Can't play this video", fontSize = 17.sp, color = Color.White)
                    Text(msg, fontSize = 13.sp, color = Color(0xCCFFFFFF), maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp))
                    TextButton(onClick = { playError = null; buffering = true; exoPlayer.prepare(); exoPlayer.play() }) {
                        Text("Retry", color = AccentBlueLight)
                    }
                }
            }

            if (controlsVisible && !inPip) {
                // top scrim
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.TopCenter)
                        .background(Brush.verticalGradient(listOf(Color(0xCC000000), Color.Transparent)))
                        .padding(horizontal = 4.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { if (fullscreen) setFullscreen(false) else onBack() }) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = Color.White)
                    }
                    Column(Modifier.weight(1f)) {
                        Text(videoTitle, fontSize = 17.sp, fontWeight = FontWeight.Light, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(if (isLocalFile) "Saved file" else "Streaming", fontSize = 13.sp, fontWeight = FontWeight.Light, color = Color(0xCCFFFFFF))
                    }
                    IconButton(onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && activity != null) {
                            activity.enterPictureInPictureMode(pip.params(false))
                        }
                    }) { Icon(Icons.Outlined.PictureInPictureAlt, "Picture-in-picture", tint = Color.White) }
                }

                // centre play / pause
                Row(
                    Modifier.align(Alignment.Center),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { exoPlayer.seekTo((exoPlayer.currentPosition - 10_000).coerceAtLeast(0)) }) {
                        Icon(Icons.Outlined.Replay10, "Back 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                    Box(
                        Modifier
                            .size(60.dp)
                            .clip(RoundedCornerShape(30.dp))
                            .background(Color(0x99000000))
                            .clickable {
                                if (isPlaying) exoPlayer.pause()
                                else {
                                    // Stopped after handing the video to another player.
                                    if (exoPlayer.playbackState == Player.STATE_IDLE) exoPlayer.prepare()
                                    exoPlayer.play()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                            "Play or pause", tint = Color.White, modifier = Modifier.size(34.dp)
                        )
                    }
                    IconButton(onClick = { exoPlayer.seekTo((exoPlayer.currentPosition + 10_000).coerceAtMost(duration)) }) {
                        Icon(Icons.Outlined.Forward10, "Forward 10 seconds", tint = Color.White, modifier = Modifier.size(34.dp))
                    }
                }

                // bottom scrim: seek + row of tools
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Slider(
                            value = currentPosition.toFloat().coerceIn(0f, duration.toFloat()),
                            onValueChange = { currentPosition = it.toLong(); exoPlayer.seekTo(it.toLong()) },
                            valueRange = 0f..duration.toFloat(),
                            modifier = Modifier.weight(1f).height(28.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = Color.White,
                                activeTrackColor = AccentBlueLight,
                                inactiveTrackColor = Color(0x55FFFFFF)
                            )
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("${fmt(currentPosition)} / ${fmt(duration)}", fontFamily = JetBrainsMono, fontSize = 12.sp, color = Color.White)
                    }
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showTrackSheet = true }) { Icon(Icons.Outlined.MusicNote, "Audio", tint = Color.White) }
                        IconButton(onClick = { showTrackSheet = true }) { Icon(Icons.Outlined.ClosedCaption, "Subtitles", tint = Color.White) }
                        Text(
                            String.format("%.1f×", playbackSpeed),
                            fontFamily = JetBrainsMono, fontSize = 13.sp, color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x44FFFFFF))
                                .clickable {
                                    playbackSpeed = when (playbackSpeed) { 1.0f -> 1.25f; 1.25f -> 1.5f; 1.5f -> 2.0f; else -> 1.0f }
                                    exoPlayer.setPlaybackSpeed(playbackSpeed)
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            pictureModes[pictureMode],
                            fontFamily = JetBrainsMono, fontSize = 13.sp, color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0x44FFFFFF))
                                .clickable { pictureMode = (pictureMode + 1) % pictureModes.size }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        )
                        IconButton(onClick = { setFullscreen(!fullscreen) }) {
                            Icon(
                                if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                                "Fullscreen", tint = Color.White
                            )
                        }
                    }
                }
            }
        }

        // Info panel (portrait only)
        if (!fullscreen && !inPip) {
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Text(videoTitle, fontSize = 24.sp, fontWeight = FontWeight.Light, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        channelName ?: if (isLocalFile) null else "Streaming",
                        resolution.ifBlank { null }, videoCodec, fileSize.takeIf { it > 0 }?.let { formatSize(it) }, audioCodec
                    ).joinToString(" · ").ifBlank { if (isLocalFile) "Saved file" else "Streaming" },
                    fontSize = 16.sp, fontWeight = FontWeight.Light, color = colors.textSecondary,
                    modifier = Modifier.padding(top = 6.dp)
                )

                KinoCard(modifier = Modifier.padding(top = 18.dp)) {
                    Row(Modifier.fillMaxWidth()) {
                        InfoCell("Source", if (isLocalFile) "Saved file" else "Stream", Modifier.weight(1f))
                        InfoCell("Bitrate", bitrate, Modifier.weight(1f))
                    }
                    Row(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                        InfoCell("Decoder", "Hardware", Modifier.weight(1f))
                        InfoCell("Speed", String.format("%.2f×", playbackSpeed), Modifier.weight(1f))
                    }
                    KinoButton(
                        "Open in another player", icon = Icons.AutoMirrored.Outlined.OpenInNew, tonal = true,
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                        onClick = { showPlayerPicker = true }
                    )
                }

                if (!isLocalFile) {
                    Text("Keep this file", fontSize = 18.sp, fontWeight = FontWeight.Light, color = colors.textSecondary, modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))
                    KinoSegmented(listOf("Discard", "7 days", "Keep", "Save"), keepMode, { keepMode = it })
                    Row(Modifier.padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        KinoButton("Save to Internal", icon = Icons.Outlined.FileDownload, modifier = Modifier.weight(1f), onClick = { save() })
                        KinoButton("Add to queue", icon = Icons.AutoMirrored.Outlined.PlaylistAdd, tonal = true, modifier = Modifier.weight(1f), onClick = { save() })
                    }
                }
                savedMsg?.let {
                    Text(
                        it,
                        fontSize = 16.sp, fontWeight = FontWeight.Light, color = colors.textPrimary,
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }

    fun openIn(player: ExternalPlayer, pkg: String?) {
        val resumeAt = exoPlayer.currentPosition
        // Pause first and switch PiP off: leaving for another player must not float Playgram's own video.
        exoPlayer.pause()
        pip.playing = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) { try { activity?.setPictureInPictureParams(pip.params(false)) } catch (_: Exception) {} }
        try {
            context.startActivity(app.playgram.core.player.ExternalPlayers.viewIntent(context, videoUrl, playUrl, videoTitle, resumeAt, pkg, forceChooser = true))
            // Only one player should pull the stream; Playgram's own stops here.
            handedOff = videoUrl.startsWith("tg://")
            saveProgress()
            exoPlayer.pause()
            exoPlayer.stop()
        } catch (_: android.content.ActivityNotFoundException) {
            savedMsg = "${player.name} couldn't open this video."
        } catch (e: Exception) {
            savedMsg = "Couldn't share this video: ${e.message}"
        }
    }

    if (showPlayerPicker) {
        AlertDialog(
            onDismissRequest = { showPlayerPicker = false },
            title = { Text("Open in another player", fontWeight = FontWeight.Light, color = colors.textPrimary) },
            text = {
                Column {
                    externalPlayers.forEach { player ->
                        val pkg = installedPackage(context, player)
                        val available = player.packages.isEmpty() || pkg != null
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .clickable {
                                    showPlayerPicker = false
                                    if (available) openIn(player, pkg)
                                    else try {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${player.packages.last()}")))
                                    } catch (_: Exception) {
                                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${player.packages.last()}")))
                                    }
                                }
                                .padding(horizontal = 8.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (player.packages.isEmpty()) Icons.Outlined.Apps else Icons.Outlined.PlayCircle, null,
                                tint = if (available) AccentBlueLight else colors.textSecondary
                            )
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(player.name, fontSize = 17.sp, color = if (available) colors.textPrimary else colors.textSecondary)
                                Text(
                                    when {
                                        player.packages.isEmpty() -> "Pick any installed video app"
                                        available -> "Installed"
                                        else -> "Not installed · tap to get it"
                                    },
                                    fontSize = 13.sp, fontWeight = FontWeight.Light, color = colors.textSecondary
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPlayerPicker = false }) { Text("Cancel", color = AccentBlueLight) } },
            containerColor = colors.surfaceVariant
        )
    }

    if (showTrackSheet) {
        AlertDialog(
            onDismissRequest = { showTrackSheet = false },
            title = { Text("Audio & subtitles", fontWeight = FontWeight.Light, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Audio", fontSize = 15.sp, color = colors.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KinoPill("Track 1", true, {})
                        KinoPill("Track 2", false, {})
                    }
                    Text("Subtitles", fontSize = 15.sp, color = colors.textSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        KinoPill("Off", true, {})
                        KinoPill("English", false, {})
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showTrackSheet = false }) { Text("Close", color = AccentBlueLight) } },
            containerColor = colors.surfaceVariant
        )
    }
}

@Composable
private fun InfoCell(label: String, value: String, modifier: Modifier = Modifier) {
    val colors = LocalPlaygramColors.current
    Column(modifier) {
        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
        Text(value, fontFamily = JetBrainsMono, fontSize = 20.sp, color = colors.textPrimary)
    }
}
