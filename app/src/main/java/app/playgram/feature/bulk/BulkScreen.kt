package app.playgram.feature.bulk

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.playgram.PlaygramApp
import app.playgram.core.design.*
import app.playgram.feature.navigation.NavRoutes

@Composable
fun BulkScreen(
    chatId: Long,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    app.playgram.core.design.KinoScaled { BulkScreenContent(chatId = chatId, onBack = onBack, onNavigate = onNavigate) }
}

@Composable
private fun BulkScreenContent(
    chatId: Long,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit
) {
    val colors = LocalPlaygramColors.current
    val telegramManager = PlaygramApp.instance.telegramManager
    val downloadManager = PlaygramApp.instance.downloadManager
    val chats by telegramManager.chats.collectAsState()

    val sources = remember { mutableStateListOf<Long>().apply { if (chatId != 0L) add(chatId) } }
    var showSourcePicker by remember { mutableStateOf(false) }

    var videoOnly by remember { mutableStateOf(0) }
    var dateFrom by remember { mutableStateOf("Any") }
    var dateTo by remember { mutableStateOf("Any") }
    var resolution by remember { mutableStateOf("Any") }
    var minSize by remember { mutableStateOf("Any") }
    var caption by remember { mutableStateOf("") }

    fun minBytes(): Long = when (minSize) {
        "> 500 MB" -> 500L * 1024 * 1024
        "> 700 MB" -> 700L * 1024 * 1024
        "> 1 GB" -> 1024L * 1024 * 1024
        "> 4 GB" -> 4096L * 1024 * 1024
        else -> 0L
    }

    val matches = sources.flatMap { telegramManager.getSampleMediaForChat(it) }.filter { m ->
        val q = m.quality.lowercase()
        val resOk = when (resolution) {
            "720p+" -> listOf("720", "1080", "2160", "4k").any { q.contains(it) }
            "1080p+" -> listOf("1080", "2160", "4k").any { q.contains(it) }
            "2160p" -> q.contains("2160") || q.contains("4k")
            else -> true
        }
        resOk && m.sizeBytes >= minBytes() && m.title.contains(caption.trim(), ignoreCase = true)
    }.distinctBy { it.id }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        KinoTopBar(
            title = "New bulk job",
            onBack = onBack,
            actions = {
                Box(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(16.dp))
                        .padding(horizontal = 18.dp, vertical = 9.dp)
                ) { Text("Templates", fontSize = 16.sp, fontWeight = FontWeight.Light, color = colors.textPrimary) }
            }
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
        ) {
            Label("SOURCE", Modifier.padding(top = 8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .border(1.dp, colors.border, RoundedCornerShape(20.dp))
                        .clickable { showSourcePicker = true }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Add, null, tint = colors.textPrimary, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Add", fontSize = 16.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
                }
                sources.forEach { id ->
                    KinoPill(chats.find { it.id == id }?.title ?: "Chat", true, { sources.remove(id) })
                }
            }
            Text(
                if (sources.isEmpty()) "0 sources · tap + Add to pick channels"
                else "${sources.size} source${if (sources.size > 1) "s" else ""} · ${matches.size} matching files",
                fontSize = 15.sp, fontWeight = FontWeight.Light, color = colors.textSecondary,
                modifier = Modifier.padding(top = 10.dp)
            )

            Label("MATCH", Modifier.padding(top = 22.dp, bottom = 8.dp))
            KinoCard {
                SubLabel("Type")
                KinoSegmented(listOf("Video only", "All media"), videoOnly, { videoOnly = it }, Modifier.padding(top = 8.dp))
                SubLabel("Date", Modifier.padding(top = 16.dp))
                ChipRow("From", listOf("Any", "2026", "2025", "2024", "2023"), dateFrom) { dateFrom = it }
                ChipRow("To", listOf("Any", "2026", "2025", "2024", "2023"), dateTo) { dateTo = it }
                SubLabel("Resolution", Modifier.padding(top = 16.dp))
                ChipRow(null, listOf("Any", "720p+", "1080p+", "2160p"), resolution) { resolution = it }
                SubLabel("Size", Modifier.padding(top = 16.dp))
                ChipRow(null, listOf("Any", "> 500 MB", "> 700 MB", "> 1 GB", "> 4 GB"), minSize) { minSize = it }
                SubLabel("Caption / filename", Modifier.padding(top = 16.dp))
                Box(
                    Modifier
                        .padding(top = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceElevated)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                ) {
                    if (caption.isEmpty()) Text("contains, e.g. lecture", fontSize = 17.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
                    BasicTextField(
                        value = caption,
                        onValueChange = { caption = it },
                        singleLine = true,
                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 17.sp),
                        cursorBrush = SolidColor(AccentBlueLight),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Column(
            Modifier
                .fillMaxWidth()
                .background(colors.surface)
                .padding(16.dp)
        ) {
            if (sources.isEmpty()) {
                Text("Add a source first", fontSize = 16.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
                Spacer(Modifier.height(10.dp))
            } else {
                val totalMb = matches.sumOf { it.sizeBytes } / (1024.0 * 1024.0)
                Text(
                    String.format("%d files · %.1f MB", matches.size, totalMb),
                    fontSize = 16.sp, fontWeight = FontWeight.Light, color = colors.textSecondary
                )
                Spacer(Modifier.height(10.dp))
            }
            KinoButton(
                "Start job",
                enabled = sources.isNotEmpty() && matches.isNotEmpty(),
                onClick = {
                    matches.forEach { media ->
                        downloadManager.enqueueDownload(
                            id = media.id,
                            title = media.title,
                            url = media.streamUrl,
                            totalBytes = media.sizeBytes
                        )
                    }
                    onNavigate(NavRoutes.DOWNLOADS)
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    if (showSourcePicker) {
        AlertDialog(
            onDismissRequest = { showSourcePicker = false },
            title = { Text("Pick channels", fontWeight = FontWeight.Light, color = colors.textPrimary) },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    chats.forEach { chat ->
                        val on = chat.id in sources
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { if (on) sources.remove(chat.id) else sources.add(chat.id) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(chat.title, fontSize = 16.sp, fontWeight = FontWeight.Light, color = colors.textPrimary, modifier = Modifier.weight(1f))
                            if (on) Icon(Icons.Outlined.Check, null, tint = AccentBlueLight)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSourcePicker = false }) { Text("Done", color = AccentBlueLight) }
            },
            containerColor = colors.surfaceVariant
        )
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(
        text, fontSize = 17.sp, fontWeight = FontWeight.Light,
        color = LocalPlaygramColors.current.textSecondary, letterSpacing = 1.sp,
        modifier = modifier.padding(start = 4.dp)
    )
}

@Composable
private fun SubLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, fontSize = 17.sp, fontWeight = FontWeight.Light, color = LocalPlaygramColors.current.textSecondary, modifier = modifier)
}

@Composable
private fun ChipRow(label: String?, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(top = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (label != null) {
            Text(label, fontSize = 17.sp, fontWeight = FontWeight.Light, color = LocalPlaygramColors.current.textSecondary, modifier = Modifier.width(48.dp))
        }
        options.forEach { opt -> KinoPill(opt, opt == selected, { onSelect(opt) }) }
    }
}
