package app.playgram.feature.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.playgram.PlaygramApp
import app.playgram.core.design.*
import app.playgram.core.model.DownloadStatus
import app.playgram.core.model.MediaItem
import app.playgram.core.model.TelegramMessage
import app.playgram.feature.navigation.NavRoutes
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ChatDetailScreen(
    chatId: Long,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    app.playgram.core.design.KinoScaled { ChatDetailScreenContent(chatId = chatId, onBack = onBack, onNavigate = onNavigate) }
}

@Composable
private fun ChatDetailScreenContent(
    chatId: Long,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val colors = LocalPlaygramColors.current
    val telegramManager = PlaygramApp.instance.telegramManager
    val downloadManager = PlaygramApp.instance.downloadManager

    val chats by telegramManager.chats.collectAsState()
    val chat = chats.find { it.id == chatId } ?: chats.firstOrNull()
    val isDemo by telegramManager.isDemoMode.collectAsState()
    val messages by remember(chatId, isDemo) { telegramManager.messages(chatId) }.collectAsState()
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    DisposableEffect(chatId) { onDispose { telegramManager.closeChat(chatId) } }
    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.scrollToItem(messages.size - 1) }
    LaunchedEffect(listState) {
        androidx.compose.runtime.snapshotFlow { listState.firstVisibleItemIndex }.collect { if (it == 0 && messages.size >= 20) telegramManager.loadOlder(chatId) }
    }

    var searchOpen by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<TelegramMessage>?>(null) }
    var searching by remember { mutableStateOf(false) }
    val searchListState = androidx.compose.foundation.lazy.rememberLazyListState()
    val focus = remember { androidx.compose.ui.focus.FocusRequester() }
    LaunchedEffect(query, searchOpen) {
        val q = query.trim()
        if (!searchOpen || q.isEmpty()) { results = null; searching = false; return@LaunchedEffect }
        searching = true
        kotlinx.coroutines.delay(350)
        val found = kotlinx.coroutines.suspendCancellableCoroutine<List<TelegramMessage>> { cont ->
            telegramManager.searchChat(chatId, q) { if (cont.isActive) cont.resume(it) {} }
        }
        results = found
        searching = false
        searchListState.scrollToItem(0)
    }
    LaunchedEffect(searchOpen) { if (searchOpen) focus.requestFocus() }
    fun closeSearch() { searchOpen = false; query = ""; results = null }
    androidx.activity.compose.BackHandler(enabled = searchOpen) { closeSearch() }

    val downloads by downloadManager.allDownloads.collectAsState(initial = emptyList())
    val downloadById = remember(downloads) { downloads.associateBy { it.id } }

    fun queue(media: MediaItem) {
        val existing = downloadById[media.id]
        when (existing?.status) {
            DownloadStatus.QUEUED, DownloadStatus.DOWNLOADING -> onNavigate(NavRoutes.DOWNLOADS)
            DownloadStatus.COMPLETED -> onNavigate(NavRoutes.player(existing.destinationPath, existing.title))
            DownloadStatus.PAUSED -> downloadManager.resumeDownload(media.id)
            else -> downloadManager.enqueueDownload(id = media.id, title = media.title, url = media.streamUrl, totalBytes = media.sizeBytes)
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (searchOpen) closeSearch() else onBack() }) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = colors.textPrimary)
            }
            if (searchOpen) {
                androidx.compose.foundation.text.BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = colors.textPrimary, fontSize = 18.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentBlueLight),
                    decorationBox = { inner ->
                        Box(Modifier.padding(vertical = 12.dp)) {
                            if (query.isEmpty()) Text("Search in ${chat?.title ?: "this chat"}", fontSize = 18.sp, fontWeight = FontWeight.Light, color = colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f).focusRequester(focus)
                )
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) { Icon(Icons.Outlined.Close, "Clear", tint = colors.textPrimary) }
                }
            } else {
            Box(
                Modifier.size(46.dp).clip(CircleShape).background(Color(0xFF6CB8F5)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    (chat?.title ?: "?").split(" ").take(2).joinToString("") { it.take(1) }.uppercase(),
                    fontSize = 18.sp, fontWeight = FontWeight.Light, color = Color(0xFF0E3A66)
                )
                chat?.avatarUrl?.substringAfterLast('/')?.toIntOrNull()?.let { TgImage(it, Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    chat?.title ?: "Channel",
                    fontSize = 20.sp, fontWeight = FontWeight.Light, color = colors.textPrimary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis
                )
                Text(
                    "${maxOf(messages.count { it.media != null }, chat?.mediaCount ?: 0)}${if (messages.size >= 20) "+" else ""} media files",
                    fontSize = 13.sp, fontWeight = FontWeight.Light, color = colors.textSecondary
                )
            }
            IconButton(onClick = { onNavigate(NavRoutes.bulk(chatId)) }) {
                Icon(Icons.Outlined.GridView, "Bulk job", tint = colors.textPrimary)
            }
            IconButton(onClick = { searchOpen = true }) {
                Icon(Icons.Outlined.Search, "Search", tint = colors.textPrimary)
            }
            IconButton(onClick = { onNavigate(NavRoutes.DOWNLOADS) }) {
                Icon(Icons.Outlined.MoreVert, "More", tint = colors.textPrimary)
            }
            }
        }

        if (searchOpen && query.isNotBlank()) {
            Text(
                when {
                    searching -> "Searching…"
                    results.isNullOrEmpty() -> "No results for \"${query.trim()}\""
                    else -> "${results!!.size}${if (results!!.size >= 100) "+" else ""} results"
                },
                fontSize = 14.sp, fontWeight = FontWeight.Light, color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        val shown = if (searchOpen && query.isNotBlank()) results.orEmpty() else messages
        LazyColumn(
            state = if (searchOpen && query.isNotBlank()) searchListState else listState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(shown, key = { it.id }) { msg ->
                ChatMessageCard(
                    message = msg,
                    channelName = chat?.title ?: "",
                    onPlay = { onNavigate(NavRoutes.player(it.streamUrl, it.title)) },
                    onDownload = { queue(it) },
                    download = msg.media?.let { downloadById[it.id] }
                )
            }
        }
    }
}

@Composable
fun ChatMessageCard(
    message: TelegramMessage,
    channelName: String = "",
    onPlay: (MediaItem) -> Unit,
    onDownload: (MediaItem) -> Unit,
    download: app.playgram.core.model.DownloadItem? = null
) {
    val colors = LocalPlaygramColors.current
    val media = message.media
    val time = remember(message.dateMs) { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.dateMs)) }

    Column(
        Modifier
            .fillMaxWidth(0.94f)
            .clip(RoundedCornerShape(22.dp))
            .background(colors.surfaceVariant)
    ) {
        if (media != null) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(190.dp)
                    .background(Brush.verticalGradient(listOf(Color(0xFF3A4A52), Color(0xFF1F2A30))))
                    .clickable { onPlay(media) },
                contentAlignment = Alignment.Center
            ) {
                media.thumbnailUrl?.takeIf { it.startsWith("tg://file/") }?.substringAfterLast('/')?.toIntOrNull()?.let {
                    TgImage(it, Modifier.fillMaxSize())
                }
                Box(
                    Modifier.size(56.dp).clip(CircleShape).background(Color(0x99000000)),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(media.quality, media.mimeType.substringAfter('/').uppercase()).forEach { tag ->
                        Text(
                            tag, fontSize = 13.sp, color = Color.White,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0x99000000))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    "${media.formattedDuration} · ${media.formattedSize}",
                    fontFamily = JetBrainsMono, fontSize = 14.sp, color = Color.White,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0x99000000))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        message.photoFileId?.let { TgImage(it, Modifier.fillMaxWidth().heightIn(min = 120.dp, max = 360.dp), fit = true) }
        Column(Modifier.padding(14.dp)) {
            Text(
                media?.title ?: message.text,
                fontSize = 18.sp, fontWeight = FontWeight.Light, color = colors.textPrimary,
                maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (media != null) {
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val pct = download?.let { "${(it.progress * 100).toInt()}%" } ?: ""
                    KinoButton(
                        when (download?.status) {
                            DownloadStatus.QUEUED -> "Queued"
                            DownloadStatus.DOWNLOADING -> "Downloading $pct"
                            DownloadStatus.PAUSED -> "Resume · $pct"
                            DownloadStatus.COMPLETED -> "Play saved file"
                            DownloadStatus.FAILED -> "Retry download"
                            null -> "Download"
                        },
                        onClick = { onDownload(media) },
                        icon = if (download?.status == DownloadStatus.COMPLETED) Icons.Outlined.CheckCircle else null,
                        tonal = download?.status == DownloadStatus.COMPLETED,
                        modifier = Modifier.weight(1f)
                    )
                    KinoButton(
                        if (download == null) "Add to queue" else "In Downloads",
                        onClick = { onDownload(media) }, tonal = true, modifier = Modifier.weight(1f)
                    )
                }
                if (download?.status == DownloadStatus.DOWNLOADING || download?.status == DownloadStatus.PAUSED) {
                    LinearProgressIndicator(
                        progress = { download.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp).clip(RoundedCornerShape(2.dp)),
                        color = AccentBlueLight,
                        trackColor = colors.surface
                    )
                }
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    KinoButton("Stream", onClick = { onPlay(media) }, tonal = true, modifier = Modifier.weight(1f))
                    KinoButton("Info", onClick = { onPlay(media) }, tonal = true, modifier = Modifier.weight(1f))
                    KinoButton("More", onClick = {}, tonal = true, modifier = Modifier.weight(1f))
                }
                if (message.text.isNotBlank() && message.text != media.title) {
                    Text(
                        message.text,
                        fontSize = 16.sp, fontWeight = FontWeight.Light, color = colors.textPrimary,
                        modifier = Modifier.padding(top = 12.dp)
                    )
                }
            }
            Text(
                "$channelName · $time",
                fontSize = 14.sp, fontWeight = FontWeight.Light, color = colors.textSecondary,
                modifier = Modifier.align(Alignment.End).padding(top = 8.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Shows a Telegram file (thumbnail or photo) once TDLib has downloaded it. */
@Composable
fun TgImage(fileId: Int, modifier: Modifier = Modifier, fit: Boolean = false) {
    val tm = PlaygramApp.instance.telegramManager
    val bitmap by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, fileId) {
        value = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            tm.fetchFilePath(fileId) { path ->
                val bmp = path?.let { runCatching { android.graphics.BitmapFactory.decodeFile(it)?.asImageBitmap() }.getOrNull() }
                if (cont.isActive) cont.resume(bmp) {}
            }
        }
    }
    bitmap?.let {
        androidx.compose.foundation.Image(
            it, null, modifier,
            contentScale = if (fit) androidx.compose.ui.layout.ContentScale.FillWidth else androidx.compose.ui.layout.ContentScale.Crop
        )
    }
}
