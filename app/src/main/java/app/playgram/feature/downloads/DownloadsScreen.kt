package app.playgram.feature.downloads

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.playgram.PlaygramApp
import app.playgram.core.design.AccentBlue
import app.playgram.core.design.AccentBlueLight
import app.playgram.core.design.AccentContainer
import app.playgram.core.design.JetBrainsMono
import app.playgram.core.design.LocalPlaygramColors
import app.playgram.core.design.WarningAmber
import app.playgram.core.model.DownloadItem
import app.playgram.core.model.DownloadStatus
import app.playgram.feature.navigation.NavRoutes
import kotlinx.coroutines.delay

private fun freeStorageGb(): Double = try {
    val st = StatFs(Environment.getExternalStorageDirectory().path)
    st.availableBytes / (1024.0 * 1024.0 * 1024.0)
} catch (e: Exception) { 0.0 }

@Composable
fun DownloadsScreen(
    onNavigate: (String) -> Unit
) {
    val colors = LocalPlaygramColors.current
    val downloadManager = PlaygramApp.instance.downloadManager
    val downloads by downloadManager.allDownloads.collectAsState(initial = emptyList())

    var selectedTab by remember { mutableStateOf(0) }
    var mode by remember { mutableStateOf(1) }
    var pausedAll by remember { mutableStateOf(false) }

    val activeList = downloads.filter { it.status != DownloadStatus.COMPLETED }
    val completedList = downloads.filter { it.status == DownloadStatus.COMPLETED }

    val totalSpeed = activeList.sumOf { it.speedBytesPerSec }
    val speedMb = totalSpeed / (1024.0 * 1024.0)

    // 60 s rolling throughput history for the sparkline
    val history = remember { mutableStateListOf<Float>().apply { repeat(60) { add(0f) } } }
    val latestSpeed by rememberUpdatedState(speedMb.toFloat())
    LaunchedEffect(Unit) {
        while (true) {
            history.removeAt(0); history.add(latestSpeed)
            delay(1000)
        }
    }
    val freeGb = remember(downloads) { freeStorageGb() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Title row
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Downloads",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Light,
                    color = colors.textPrimary,
                    modifier = Modifier.weight(1f)
                )
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceElevated)
                        .clickable {
                            pausedAll = !pausedAll
                            activeList.forEach {
                                if (pausedAll) downloadManager.pauseDownload(it.id)
                                else downloadManager.resumeDownload(it.id)
                            }
                        }
                        .padding(horizontal = 18.dp, vertical = 12.dp)
                ) {
                    Text(
                        if (pausedAll) "Resume all" else "Pause all",
                        fontSize = 12.sp,
                        color = colors.textPrimary
                    )
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.MoreVert, "More", tint = colors.textPrimary)
                }
            }
        }

        // Queue / All files
        item {
            Segmented(
                options = listOf("Queue", "All files"),
                selected = selectedTab,
                onSelect = { selectedTab = it },
                textSize = 15.sp
            )
        }

        if (selectedTab == 0) {
            item {
                Segmented(
                    options = listOf("Eco", "Balanced", "Max", "Auto"),
                    selected = mode,
                    onSelect = { mode = it },
                    textSize = 14.sp
                )
            }

            // Throughput card
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(24.dp))
                        .background(colors.surfaceVariant)
                        .padding(20.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(
                            String.format("%.1f MB/s", speedMb).replace(".0 ", " "),
                            fontFamily = JetBrainsMono,
                            fontSize = 29.sp,
                            color = colors.textPrimary
                        )
                        Text("60 s", fontFamily = JetBrainsMono, fontSize = 11.sp, color = colors.textSecondary)
                    }
                    Row(
                        Modifier.fillMaxWidth().height(40.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (pausedAll) "paused" else if (activeList.isEmpty()) "idle" else "downloading",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Light,
                            color = colors.textSecondary
                        )
                        Spacer(Modifier.width(24.dp))
                        Sparkline(history, Modifier.weight(1f).height(28.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Stat(activeList.count { it.status == DownloadStatus.DOWNLOADING }.toString(), "Active")
                        Stat(activeList.count { it.status != DownloadStatus.DOWNLOADING && it.status != DownloadStatus.FAILED }.toString(), "Queued")
                        Stat(activeList.count { it.status == DownloadStatus.FAILED }.toString(), "Failed")
                        Stat(String.format("%.1f GB", freeGb), "Free")
                    }
                }
            }

            // State chip + override
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(Color(0xFF232628))
                            .border(1.dp, Color(0xFF4A4F52), RoundedCornerShape(20.dp))
                            .padding(horizontal = 18.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(10.dp).clip(CircleShape).background(if (pausedAll) WarningAmber else colors.statusSuccess))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            if (pausedAll) "Paused · cooling down" else "Running · ${listOf("Eco", "Balanced", "Max", "Auto")[mode].lowercase()}",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Light,
                            color = colors.textPrimary
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(colors.surfaceElevated)
                            .clickable {
                                pausedAll = false
                                activeList.forEach { downloadManager.resumeDownload(it.id) }
                            }
                            .padding(horizontal = 22.dp, vertical = 14.dp)
                    ) { Text("Override", fontSize = 14.sp, color = colors.textPrimary) }
                }
            }

            if (activeList.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(top = 36.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            Modifier.size(116.dp).clip(CircleShape).background(AccentContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.FileDownload, null, tint = AccentBlueLight, modifier = Modifier.size(52.dp))
                        }
                        Spacer(Modifier.height(24.dp))
                        Text("Nothing in the queue", fontSize = 20.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Open a channel, pick a video and tap Download. Finished files move to Library.",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Light,
                            textAlign = TextAlign.Center,
                            color = colors.textSecondary
                        )
                    }
                }
            } else {
                items(activeList, key = { it.id }) { item ->
                    DownloadItemCard(
                        item = item,
                        onPause = { downloadManager.pauseDownload(item.id) },
                        onResume = { downloadManager.resumeDownload(item.id) },
                        onCancel = { downloadManager.cancelDownload(item.id) },
                        onPlay = { onNavigate(NavRoutes.player(item.destinationPath, item.title)) }
                    )
                }
            }
        } else {
            if (completedList.isEmpty()) {
                item {
                    Text(
                        "No files yet",
                        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        color = colors.textSecondary
                    )
                }
            } else {
                items(completedList, key = { it.id }) { item ->
                    DownloadItemCard(
                        item = item,
                        onPause = {}, onResume = {},
                        onCancel = { downloadManager.cancelDownload(item.id) },
                        onPlay = { onNavigate(NavRoutes.player(item.destinationPath, item.title)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun Segmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    textSize: androidx.compose.ui.unit.TextUnit
) {
    val colors = LocalPlaygramColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(colors.surfaceVariant)
            .padding(6.dp)
    ) {
        options.forEachIndexed { i, label ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (i == selected) AccentBlue else Color.Transparent)
                    .clickable { onSelect(i) }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    fontSize = textSize,
                    fontWeight = FontWeight.Light,
                    color = if (i == selected) Color.White else colors.textSecondary
                )
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    val colors = LocalPlaygramColors.current
    Column {
        Text(value, fontFamily = JetBrainsMono, fontSize = 20.sp, color = colors.textPrimary)
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
    }
}

@Composable
private fun Sparkline(values: List<Float>, modifier: Modifier = Modifier) {
    val peak = (values.maxOrNull() ?: 0f).coerceAtLeast(1f)
    Canvas(modifier) {
        val step = size.width / (values.size - 1).coerceAtLeast(1)
        val pts = values.mapIndexed { i, v -> Offset(i * step, size.height - (v / peak) * (size.height - 6f) - 3f) }
        for (i in 0 until pts.size - 1) {
            drawLine(AccentBlueLight, pts[i], pts[i + 1], strokeWidth = 3f, cap = StrokeCap.Round)
        }
        drawCircle(AccentBlueLight, 7f, pts.last())
    }
}

@Composable
fun DownloadItemCard(
    item: DownloadItem,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onCancel: () -> Unit,
    onPlay: () -> Unit
) {
    val colors = LocalPlaygramColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    item.title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (item.status) {
                        DownloadStatus.COMPLETED -> colors.statusSuccess.copy(alpha = 0.2f)
                        DownloadStatus.PAUSED -> colors.statusWarning.copy(alpha = 0.2f)
                        DownloadStatus.FAILED -> colors.statusError.copy(alpha = 0.2f)
                        else -> colors.accent.copy(alpha = 0.2f)
                    }
                ) {
                    Text(
                        item.status.name,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (item.status) {
                            DownloadStatus.COMPLETED -> colors.statusSuccess
                            DownloadStatus.PAUSED -> colors.statusWarning
                            DownloadStatus.FAILED -> colors.statusError
                            else -> colors.accent
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (item.status != DownloadStatus.COMPLETED) {
                LinearProgressIndicator(
                    progress = { item.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = colors.accent,
                    trackColor = colors.surfaceElevated
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    val downloadedMb = item.downloadedBytes / (1024.0 * 1024.0)
                    val totalMb = item.totalBytes / (1024.0 * 1024.0)
                    Text(
                        String.format("%.1f MB / %.1f MB", downloadedMb, totalMb),
                        fontSize = 11.sp,
                        color = colors.textSecondary
                    )
                    Text(
                        item.formattedSpeed,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = colors.accent
                    )
                }
            } else {
                Text(
                    "Saved to: ${item.destinationPath}",
                    fontSize = 11.sp,
                    color = colors.textTertiary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (item.status == DownloadStatus.COMPLETED) {
                    Button(
                        onClick = onPlay,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Play Local", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = colors.textTertiary)
                    }
                } else {
                    if (item.status == DownloadStatus.DOWNLOADING) {
                        IconButton(onClick = onPause, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Pause, contentDescription = "Pause", tint = colors.textPrimary)
                        }
                    } else if (item.status == DownloadStatus.PAUSED) {
                        IconButton(onClick = onResume, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "Resume", tint = colors.accent)
                        }
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    IconButton(onClick = onCancel, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Cancel", tint = colors.textTertiary)
                    }
                }
            }
        }
    }
}
