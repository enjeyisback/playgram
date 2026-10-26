package app.playgram.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.playgram.PlaygramApp
import app.playgram.core.design.AccentBlueLight
import app.playgram.core.design.AccentContainer
import app.playgram.core.design.AvatarPink
import app.playgram.core.design.LocalPlaygramColors
import app.playgram.core.design.StatusOnline
import app.playgram.core.model.MediaItem
import app.playgram.core.model.TelegramChat
import app.playgram.feature.auth.TelegramLoginDialog
import app.playgram.feature.navigation.NavRoutes
import kotlinx.coroutines.launch
import java.util.Calendar

private fun greeting(): String = when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Good night"
}

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    scrollToTop: Int = 0
) {
    val colors = LocalPlaygramColors.current
    val telegramManager = PlaygramApp.instance.telegramManager
    val downloadManager = PlaygramApp.instance.downloadManager

    val chats by telegramManager.chats.collectAsState()
    val isDemoMode by telegramManager.isDemoMode.collectAsState()
    val userProfile by telegramManager.userProfile.collectAsState()
    val activeDownloads by downloadManager.activeDownloads.collectAsState(initial = emptyList())

    var showLoginDialog by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val history by PlaygramApp.instance.database.watchPositionDao().getAllWatchHistory().collectAsState(initial = emptyList())
    val continueWatching = history.filter { !it.completed && it.positionMs > 0 }.take(10)
    var channelQuery by remember { mutableStateOf("") }
    val defaultChatId by telegramManager.defaultChatId.collectAsState()
    val defaultChat = chats.find { it.id == defaultChatId }
    val channelMessages by remember(defaultChatId) { telegramManager.messages(defaultChatId) }.collectAsState()
    // Typing searches Telegram's whole history of the channel, not just what is loaded on screen.
    val q = channelQuery.trim()
    var serverHits by remember(defaultChatId) { mutableStateOf<List<MediaItem>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    LaunchedEffect(q, defaultChatId) {
        if (q.isEmpty()) { serverHits = emptyList(); searching = false; return@LaunchedEffect }
        searching = true
        delay(400)
        val found = kotlinx.coroutines.suspendCancellableCoroutine<List<app.playgram.core.model.TelegramMessage>> { cont ->
            telegramManager.searchVideos(defaultChatId, q) { if (cont.isActive) cont.resume(it) {} }
        }
        serverHits = found.mapNotNull { it.media }
        searching = false
    }
    // Newest first, videos only, in rows of two.
    val videoRows = remember(channelMessages, q, serverHits) {
        val all = channelMessages.mapNotNull { it.media }.reversed()
        val shown = if (q.isEmpty()) all else {
            val words = searchKey(q).split(' ').filter { it.isNotEmpty() }
            // Telegram's search is fuzzy (it also matches captions and unrelated text); keep only real title matches.
            val matches = { m: MediaItem -> val t = searchKey(m.title); words.all { t.contains(it) } }
            (all.filter(matches) + serverHits.filter(matches)).distinctBy { it.id }.sortedByDescending { it.id.substringAfterLast('_').toLongOrNull() ?: 0L }
        }
        shown.chunked(2)
    }
    val listState = rememberLazyListState()
    LaunchedEffect(scrollToTop) { if (scrollToTop > 0) listState.animateScrollToItem(0) }

    val name = userProfile?.firstName?.takeIf { it.isNotBlank() } ?: "there"
    val connected = userProfile != null && !isDemoMode

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        // Greeting header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(AvatarPink)
                        .clickable {
                            if (userProfile == null || isDemoMode) {
                                telegramManager.startLogin()
                                showLoginDialog = true
                            }
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        name.take(1).uppercase(),
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Light,
                        color = Color(0xFF3A1D2B)
                    )
                    // Telegram profile photo over the initial; the initial stays if there is no photo.
                    userProfile?.avatarUrl?.substringAfterLast('/')?.toIntOrNull()?.let {
                        app.playgram.feature.chats.TgImage(it, Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "${greeting()}, $name",
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Light,
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = !connected) {
                            telegramManager.startLogin()
                            showLoginDialog = true
                        }
                    ) {
                        Box(
                            Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (connected) StatusOnline else colors.statusWarning)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (connected) "Connected" else "Sample data · tap to log in",
                            fontSize = 12.sp,
                            color = if (connected) StatusOnline else colors.textSecondary
                        )
                    }
                }
            }
        }

        // Search bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, null, tint = AccentBlueLight)
                Spacer(Modifier.width(14.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = channelQuery,
                    onValueChange = { channelQuery = it },
                    singleLine = true,
                    textStyle = androidx.compose.ui.text.TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(AccentBlueLight),
                    decorationBox = { inner ->
                        Box {
                            if (channelQuery.isEmpty()) Text("Search videos", fontSize = 14.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                if (channelQuery.isNotEmpty()) {
                    Icon(
                        Icons.Outlined.Close, "Clear search", tint = colors.textSecondary,
                        modifier = Modifier.size(20.dp).clickable { channelQuery = "" }
                    )
                }
            }
        }

        // Active download strip
        if (activeDownloads.isNotEmpty()) {
            item {
                val first = activeDownloads.first()
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .clickable { onNavigate(NavRoutes.DOWNLOADS) },
                    shape = RoundedCornerShape(16.dp),
                    color = AccentContainer
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.FileDownload, null, tint = AccentBlueLight)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                first.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                                color = colors.textPrimary
                            )
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { first.progress },
                                modifier = Modifier.fillMaxWidth().height(4.dp),
                                color = AccentBlueLight,
                                trackColor = colors.surface
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(first.formattedSpeed, fontSize = 12.sp, color = AccentBlueLight)
                    }
                }
            }
        }

        // Continue watching
        if (continueWatching.isNotEmpty() && channelQuery.isBlank()) item {
            SectionTitle("Continue watching", Modifier.padding(start = 16.dp, top = 8.dp, bottom = 12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(continueWatching) { w ->
                    ContinueWatchingCard(
                        watch = w,
                        onPlay = { onNavigate(NavRoutes.player(w.url, w.title)) },
                        onRemove = { scope.launch { PlaygramApp.instance.database.watchPositionDao().clearHistory(w.mediaId) } }
                    )
                }
            }
        }

        // Videos of the default channel
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 30.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    defaultChat?.title ?: "Your channel",
                    modifier = Modifier.weight(1f),
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Light,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    if (defaultChat == null) "Choose" else "Change",
                    fontSize = 14.sp,
                    color = AccentBlueLight,
                    modifier = Modifier.clickable { onNavigate(NavRoutes.LIBRARY) }.padding(start = 12.dp)
                )
            }
        }

        if (defaultChat == null) item {
            Text(
                "Pick a default channel in Library → Channels (tap the star). Its videos will show here every time you open the app.",
                fontSize = 15.sp, fontWeight = FontWeight.Light, color = colors.textSecondary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )
        } else {
            itemsIndexed(videoRows) { index, row ->
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { m ->
                        VideoGridCard(m, Modifier.weight(1f)) { onNavigate(NavRoutes.player(m.streamUrl, m.title)) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
                // Reached the end: fetch older messages (stops by itself when there are no more).
                if (index == videoRows.lastIndex) {
                    LaunchedEffect(channelMessages.size) { telegramManager.loadOlder(defaultChatId) }
                }
            }
            if (videoRows.isEmpty()) item {
                Text(
                    if (channelQuery.isNotBlank()) (if (searching) "Searching…" else "No video named \"${channelQuery.trim()}\"") else "Loading videos…",
                    fontSize = 15.sp, fontWeight = FontWeight.Light, color = colors.textSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
        }
    }

    if (showLoginDialog) {
        TelegramLoginDialog(
            onDismiss = { showLoginDialog = false },
            onLoginSuccess = { showLoginDialog = false }
        )
    }
}

/** Lower case, with underscores, dots and dashes read as spaces, so "dilo ki ram" finds Dilo_Ki_Ram_Leela. */
private fun searchKey(text: String): String = text.lowercase().replace(Regex("[_.\\-]+"), " ")

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        fontSize = 21.sp,
        fontWeight = FontWeight.Light,
        color = LocalPlaygramColors.current.textPrimary
    )
}

@Composable
fun ContinueWatchingCard(watch: app.playgram.core.database.WatchPositionEntity, onPlay: () -> Unit, onRemove: () -> Unit) {
    val fmt = { ms: Long -> val s = ms / 1000; if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60) }
    val colors = LocalPlaygramColors.current
    val thumbId = rememberWatchThumbnail(watch)
    Column(
        modifier = Modifier
            .width(232.dp)
            .clickable(onClick = onPlay)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.verticalGradient(listOf(Color(0xFF294146), Color(0xFF1F3332)))
                ),
            contentAlignment = Alignment.Center
        ) {
            thumbId?.let { app.playgram.feature.chats.TgImage(it, Modifier.fillMaxSize()) }
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(Color(0x99111A1A)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(28.dp))
            }
            RemoveBadge(onRemove, Modifier.align(Alignment.TopEnd))
            LinearProgressIndicator(
                progress = { if (watch.durationMs > 0) (watch.positionMs.toFloat() / watch.durationMs).coerceIn(0f, 1f) else 0f },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(3.dp),
                color = AccentBlueLight,
                trackColor = Color(0x55FFFFFF)
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            watch.title,
            fontSize = 16.sp,
            fontWeight = FontWeight.Light,
            color = colors.textPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            "${fmt(watch.positionMs)} / ${fmt(watch.durationMs)}",
            fontSize = 12.sp,
            fontWeight = FontWeight.Light,
            color = colors.textSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** Thumbnail of a Continue watching entry, found again through its Telegram message. */
@Composable
fun rememberWatchThumbnail(watch: app.playgram.core.database.WatchPositionEntity): Int? {
    val tm = PlaygramApp.instance.telegramManager
    val thumb by produceState<Int?>(null, watch.url) {
        value = kotlinx.coroutines.suspendCancellableCoroutine { cont ->
            tm.thumbnailFor(tm.withNameHint(watch.url, watch.title)) { if (cont.isActive) cont.resume(it) {} }
        }
    }
    return thumb
}

/** Small round ✕ that removes a title from Continue watching. */
@Composable
fun RemoveBadge(onRemove: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .padding(8.dp)
            .size(30.dp)
            .clip(CircleShape)
            .background(Color(0x99000000))
            .clickable(onClick = onRemove),
        contentAlignment = Alignment.Center
    ) {
        Icon(Icons.Outlined.Close, "Remove from Continue watching", tint = Color.White, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun ChannelFeedRow(
    chat: TelegramChat,
    onClick: () -> Unit
) {
    val colors = LocalPlaygramColors.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(AccentContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (chat.isChannel) Icons.Outlined.Campaign else Icons.Outlined.Group,
                null,
                tint = AccentBlueLight
            )
            chat.avatarUrl?.substringAfterLast('/')?.toIntOrNull()?.let { app.playgram.feature.chats.TgImage(it, Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                chat.title,
                fontSize = 15.sp,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                chat.lastMessagePreview,
                fontSize = 12.sp,
                fontWeight = FontWeight.Light,
                color = colors.textSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(chat.lastMessageTime, fontSize = 12.sp, color = colors.textTertiary)
            if (chat.unreadCount > 0) {
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .clip(CircleShape)
                        .background(colors.accent)
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                ) {
                    Text(chat.unreadCount.toString(), fontSize = 11.sp, color = Color.White)
                }
            }
        }
    }
}

/** One video in the two-column grid on Home. */
@Composable
private fun VideoGridCard(media: MediaItem, modifier: Modifier, onClick: () -> Unit) {
    val colors = LocalPlaygramColors.current
    Column(modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 10f)
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF294146), Color(0xFF1F3332)))),
            contentAlignment = Alignment.Center
        ) {
            media.thumbnailUrl?.takeIf { it.startsWith("tg://file/") }?.substringAfterLast('/')?.toIntOrNull()?.let {
                app.playgram.feature.chats.TgImage(it, Modifier.fillMaxSize())
            }
            Box(
                Modifier.size(36.dp).clip(CircleShape).background(Color(0x99111A1A)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Outlined.PlayArrow, null, tint = Color.White, modifier = Modifier.size(22.dp)) }
            Text(
                media.formattedDuration,
                fontSize = 11.sp, color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0x99000000))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            media.title,
            fontSize = 13.sp, fontWeight = FontWeight.Light, color = colors.textPrimary,
            maxLines = 2, overflow = TextOverflow.Ellipsis
        )
    }
}
