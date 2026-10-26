package app.playgram.feature.library

import android.os.Environment
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.playgram.PlaygramApp
import app.playgram.core.database.WatchPositionEntity
import app.playgram.core.design.AccentBlue
import app.playgram.core.design.AccentBlueLight
import app.playgram.core.design.AccentContainer
import app.playgram.core.design.LocalPlaygramColors
import app.playgram.core.model.DownloadStatus
import app.playgram.feature.chats.TgImage
import app.playgram.feature.home.ChannelFeedRow
import app.playgram.feature.home.rememberWatchThumbnail
import app.playgram.feature.navigation.NavRoutes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val videoExtensions = setOf("mkv", "mp4", "m4v", "webm", "avi", "mov", "ts", "mp3", "m4a", "flac")

/** A finished file in Playgram's Downloads folder. */
private data class SavedFile(val file: File) {
    val name: String get() = file.name
    val size: Long get() = file.length()
    val modified: Long get() = file.lastModified()
}

private fun formatBytes(b: Long): String = when {
    b >= 1L shl 30 -> String.format("%.2f GB", b / (1L shl 30).toDouble())
    b >= 1L shl 20 -> String.format("%.0f MB", b / (1L shl 20).toDouble())
    else -> String.format("%.0f KB", b / 1024.0)
}

private fun formatTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    return if (s >= 3600) "%d:%02d:%02d".format(s / 3600, s % 3600 / 60, s % 60) else "%d:%02d".format(s / 60, s % 60)
}

/**
 * What is yours on this phone: files saved with Download (read from the Downloads folder, so the
 * list survives restarts) and everything you have watched. Home is for discovering, this is for
 * finding again.
 */
@Composable
fun LibraryScreen(
    onNavigate: (String) -> Unit
) {
    app.playgram.core.design.KinoScaled { LibraryScreenContent(onNavigate) }
}

@Composable
private fun LibraryScreenContent(
    onNavigate: (String) -> Unit
) {
    val colors = LocalPlaygramColors.current
    val context = LocalContext.current
    val app = PlaygramApp.instance
    val scope = rememberCoroutineScope()
    val downloadsDir = remember { context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: File(context.filesDir, "Download") }

    val downloads by app.downloadManager.allDownloads.collectAsState(initial = emptyList())
    val history by app.database.watchPositionDao().getAllWatchHistory().collectAsState(initial = emptyList())

    // Files still being written belong to the Downloads tab, not here.
    val inProgress = downloads.filter { it.status != DownloadStatus.COMPLETED }.map { it.destinationPath }.toSet()
    var refresh by remember { mutableStateOf(0) }
    val saved by produceState(emptyList<SavedFile>(), refresh, downloads.size, inProgress) {
        value = withContext(Dispatchers.IO) {
            downloadsDir.listFiles().orEmpty()
                .filter { it.isFile && it.extension.lowercase() in videoExtensions && it.absolutePath !in inProgress }
                .map { SavedFile(it) }
                .sortedByDescending { it.modified }
        }
    }

    var tab by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<SavedFile?>(null) }

    val q = query.trim()
    val shownFiles = saved.filter { q.isEmpty() || it.name.contains(q, ignoreCase = true) }
    val chats by app.telegramManager.chats.collectAsState()
    val defaultChatId by app.telegramManager.defaultChatId.collectAsState()
    val shownChats = chats.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }
    val shownHistory = history.filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
        item {
            Text("Library", fontSize = 30.sp, fontWeight = FontWeight.Light, color = colors.textPrimary, modifier = Modifier.padding(top = 8.dp))
            Text(
                "${saved.size} saved · ${formatBytes(saved.sumOf { it.size })} on this phone · ${history.size} watched",
                fontSize = 14.sp, fontWeight = FontWeight.Light, color = colors.textSecondary
            )
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(colors.surfaceVariant).padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, null, tint = AccentBlueLight)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) Text(when (tab) { 0 -> "Search saved files"; 1 -> "Search watch history"; else -> "Search channels" }, fontSize = 15.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
                    BasicTextField(
                        value = query, onValueChange = { query = it }, singleLine = true,
                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 15.sp),
                        cursorBrush = SolidColor(AccentBlueLight), modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) Icon(Icons.Outlined.Close, "Clear search", tint = colors.textSecondary, modifier = Modifier.size(20.dp).clickable { query = "" })
            }
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(colors.surfaceVariant).padding(5.dp)) {
                listOf("Downloaded · ${saved.size}", "History · ${history.size}", "Channels · ${chats.size}").forEachIndexed { i, label ->
                    Box(
                        Modifier.weight(1f).clip(RoundedCornerShape(14.dp))
                            .background(if (i == tab) AccentBlue else Color.Transparent)
                            .clickable { tab = i }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(label, fontSize = 15.sp, fontWeight = FontWeight.Light, color = if (i == tab) Color.White else colors.textSecondary)
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (tab == 0) {
            if (shownFiles.isEmpty()) item {
                EmptyState(
                    if (q.isEmpty()) "No saved files yet" else "Nothing matches",
                    if (q.isEmpty()) "Tap Download on any video in a channel. Saved files play without internet and stay until you delete them." else "Try a different word."
                )
            }
            items(shownFiles, key = { it.file.absolutePath }) { f ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(colors.surfaceVariant)
                        .clickable { onNavigate(NavRoutes.player(f.file.absolutePath, f.name)) }
                        .padding(start = 14.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(44.dp).clip(CircleShape).background(AccentContainer), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.PlayArrow, null, tint = AccentBlueLight)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, fontSize = 15.sp, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${formatBytes(f.size)} · saved ${SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(f.modified))} · plays offline",
                            fontSize = 13.sp, fontWeight = FontWeight.Light, color = colors.textSecondary
                        )
                    }
                    IconButton(onClick = { confirmDelete = f }) { Icon(Icons.Outlined.DeleteOutline, "Delete file", tint = colors.textSecondary) }
                }
            }
        } else if (tab == 2) {
            item {
                Text(
                    "Tap the star to show a channel's videos on Home every time you open the app.",
                    fontSize = 13.sp, fontWeight = FontWeight.Light, color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }
            if (shownChats.isEmpty()) item {
                EmptyState(if (q.isEmpty()) "No channels yet" else "Nothing matches", if (q.isEmpty()) "Channels and groups from your Telegram account show up here." else "Try a different word.")
            }
            items(shownChats, key = { it.id }) { chat ->
                val isDefault = chat.id == defaultChatId
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f)) {
                        ChannelFeedRow(chat = chat, onClick = { onNavigate(NavRoutes.chatDetail(chat.id)) })
                    }
                    IconButton(onClick = { app.telegramManager.setDefaultChat(if (isDefault) 0L else chat.id) }) {
                        Icon(
                            if (isDefault) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                            if (isDefault) "Default channel" else "Make default channel",
                            tint = if (isDefault) AccentBlueLight else colors.textSecondary
                        )
                    }
                }
            }
        } else {
            if (shownHistory.isEmpty()) item {
                EmptyState(if (q.isEmpty()) "Nothing watched yet" else "Nothing matches", if (q.isEmpty()) "Videos you play show up here, with how far you got." else "Try a different word.")
            }
            items(shownHistory, key = { it.mediaId }) { w ->
                HistoryRow(
                    w,
                    onPlay = { onNavigate(NavRoutes.player(w.url, w.title)) },
                    onRemove = { scope.launch { app.database.watchPositionDao().clearHistory(w.mediaId) } }
                )
            }
        }
    }

    confirmDelete?.let { f ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete this file?", fontWeight = FontWeight.Light, color = colors.textPrimary) },
            text = { Text("${f.name}\n\nFrees ${formatBytes(f.size)}. It stays on Telegram, so you can stream or download it again.", color = colors.textSecondary) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = null
                    scope.launch {
                        withContext(Dispatchers.IO) { f.file.delete() }
                        downloads.firstOrNull { it.destinationPath == f.file.absolutePath }?.let { app.downloadManager.cancelDownload(it.id) }
                        refresh++
                    }
                }) { Text("Delete", color = colors.statusError) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = AccentBlueLight) } },
            containerColor = colors.surfaceVariant
        )
    }
}

@Composable
private fun HistoryRow(w: WatchPositionEntity, onPlay: () -> Unit, onRemove: () -> Unit) {
    val colors = LocalPlaygramColors.current
    val thumbId = rememberWatchThumbnail(w)
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp).clip(RoundedCornerShape(16.dp)).background(colors.surfaceVariant)
            .clickable(onClick = onPlay).padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.width(96.dp).height(56.dp).clip(RoundedCornerShape(10.dp)).background(AccentContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.PlayArrow, null, tint = AccentBlueLight)
            thumbId?.let { TgImage(it, Modifier.matchParentSize()) }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(w.title, fontSize = 15.sp, color = colors.textPrimary, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                if (w.completed) "Watched · ${formatTime(w.durationMs)}" else "${formatTime(w.positionMs)} / ${formatTime(w.durationMs)}",
                fontSize = 13.sp, fontWeight = FontWeight.Light, color = if (w.completed) AccentBlueLight else colors.textSecondary
            )
            if (!w.completed && w.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (w.positionMs.toFloat() / w.durationMs).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = AccentBlueLight, trackColor = colors.surface
                )
            }
        }
        IconButton(onClick = onRemove) { Icon(Icons.Outlined.Close, "Remove from history", tint = colors.textSecondary) }
    }
}

@Composable
private fun EmptyState(title: String, body: String) {
    val colors = LocalPlaygramColors.current
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(96.dp).clip(CircleShape).background(AccentContainer), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.VideoLibrary, null, tint = AccentBlueLight, modifier = Modifier.size(42.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
        Spacer(Modifier.height(6.dp))
        Text(body, fontSize = 14.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center, color = colors.textSecondary, modifier = Modifier.padding(horizontal = 12.dp))
    }
}
