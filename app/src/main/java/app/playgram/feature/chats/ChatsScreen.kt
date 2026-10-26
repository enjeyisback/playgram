package app.playgram.feature.chats

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.playgram.PlaygramApp
import app.playgram.core.design.AccentBlue
import app.playgram.core.design.AccentBlueLight
import app.playgram.core.design.JetBrainsMono
import app.playgram.core.design.LocalPlaygramColors
import app.playgram.core.design.StatusOnline
import app.playgram.core.model.TelegramChat
import app.playgram.feature.auth.TelegramLoginDialog
import app.playgram.feature.navigation.NavRoutes

private val avatarPalette = listOf(
    Color(0xFF6CB8F5) to Color(0xFF0E3A66),
    Color(0xFF8FDDE8) to Color(0xFF0B4A52),
    Color(0xFFF49AC1) to Color(0xFF5A1D3A),
    Color(0xFFB9A7F5) to Color(0xFF2D1F66),
    Color(0xFFA6E3A1) to Color(0xFF1C4A24)
)

private fun initials(title: String): String {
    val words = title.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    return when {
        words.isEmpty() -> "?"
        words.size == 1 -> words[0].take(2)
        else -> "${words[0].first()}${words[1].first()}"
    }.uppercase()
}

@Composable
fun ChatsScreen(
    onNavigate: (String) -> Unit
) {
    val colors = LocalPlaygramColors.current
    val telegramManager = PlaygramApp.instance.telegramManager
    val chats by telegramManager.chats.collectAsState()
    val isDemoMode by telegramManager.isDemoMode.collectAsState()
    val userProfile by telegramManager.userProfile.collectAsState()
    val downloads by PlaygramApp.instance.downloadManager.activeDownloads.collectAsState(initial = emptyList())

    var showLoginDialog by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All chats") }
    var mediaOnly by remember { mutableStateOf(false) }
    val connected = userProfile != null && !isDemoMode

    val filteredChats = chats.filter { chat ->
        val matchesSearch = chat.title.contains(searchQuery, ignoreCase = true) ||
                (chat.username?.contains(searchQuery, ignoreCase = true) ?: false)
        val matchesFilter = when (selectedFilter) {
            "Channels" -> chat.isChannel
            "Groups" -> chat.isGroup
            else -> true
        }
        matchesSearch && matchesFilter && (!mediaOnly || chat.mediaCount > 0)
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { onNavigate(NavRoutes.tools()) }) {
                    Icon(Icons.Outlined.Menu, "Menu", tint = colors.textPrimary)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Chats", fontSize = 24.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.clickable(enabled = !connected) {
                            telegramManager.startLogin(); showLoginDialog = true
                        }
                    ) {
                        Box(
                            Modifier.size(7.dp).clip(CircleShape)
                                .background(if (connected) StatusOnline else colors.statusWarning)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (connected) "Connected" else "Sample data · tap to log in",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Light,
                            color = colors.textSecondary
                        )
                    }
                }
                Box(
                    Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.surfaceVariant)
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        if (downloads.isEmpty()) "idle" else "${downloads.size} active",
                        fontFamily = JetBrainsMono,
                        fontSize = 13.sp,
                        color = colors.textSecondary
                    )
                }
                IconButton(onClick = { onNavigate(NavRoutes.DOWNLOADS) }) {
                    Icon(Icons.Outlined.MoreVert, "More", tint = colors.textPrimary)
                }
            }

            // Search bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.surfaceVariant)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.Search, null, tint = AccentBlueLight)
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            "Search chats, files, size:>1GB",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Light,
                            color = colors.textSecondary
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = colors.textPrimary, fontSize = 14.sp),
                        cursorBrush = SolidColor(AccentBlueLight),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // Dropdown chips
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DropdownChip(
                    icon = Icons.Outlined.FolderOpen,
                    label = selectedFilter,
                    options = listOf("All chats", "Channels", "Groups"),
                    onSelect = { selectedFilter = it }
                )
                DropdownChip(
                    icon = Icons.Outlined.FilterList,
                    label = if (mediaOnly) "With media" else "All types",
                    options = listOf("All types", "With media"),
                    onSelect = { mediaOnly = it == "With media" }
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                items(filteredChats) { chat ->
                    TelegramChatItem(
                        chat = chat,
                        onClick = { onNavigate(NavRoutes.chatDetail(chat.id)) }
                    )
                }
            }
        }

        // Floating actions
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SmallFloatingActionButton(
                onClick = { onNavigate(NavRoutes.tools("https://t.me/")) },
                containerColor = colors.surfaceElevated,
                contentColor = colors.textPrimary
            ) { Icon(Icons.Outlined.Edit, "New") }
            ExtendedFloatingActionButton(
                onClick = { onNavigate(NavRoutes.bulk(0L)) },
                containerColor = AccentBlue,
                contentColor = Color.White,
                icon = { Icon(Icons.Outlined.FileDownload, null) },
                text = { Text("Bulk job", fontSize = 16.sp, fontWeight = FontWeight.Light) }
            )
        }
    }

    if (showLoginDialog) {
        TelegramLoginDialog(
            onDismiss = { showLoginDialog = false },
            onLoginSuccess = { showLoginDialog = false }
        )
    }
}

@Composable
private fun DropdownChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    options: List<String>,
    onSelect: (String) -> Unit
) {
    val colors = LocalPlaygramColors.current
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(14.dp))
                .background(colors.surfaceVariant)
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, fontSize = 15.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
            Spacer(Modifier.width(4.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = colors.textSecondary, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { opt ->
                DropdownMenuItem(text = { Text(opt) }, onClick = { onSelect(opt); open = false })
            }
        }
    }
}

@Composable
fun TelegramChatItem(
    chat: TelegramChat,
    onClick: () -> Unit,
    onBulkClick: () -> Unit = {}
) {
    val colors = LocalPlaygramColors.current
    val (bg, fg) = avatarPalette[(chat.id.hashCode().and(0x7fffffff)) % avatarPalette.size]

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(54.dp).clip(CircleShape).background(bg),
            contentAlignment = Alignment.Center
        ) {
            Text(initials(chat.title), fontSize = 20.sp, fontWeight = FontWeight.Light, color = fg)
            chat.avatarUrl?.substringAfterLast('/')?.toIntOrNull()?.let { TgImage(it, Modifier.fillMaxSize()) }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.title,
                    fontSize = 17.sp,
                    color = colors.textPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (chat.isMuted) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.VolumeOff, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                }
                if (chat.isPinned) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Outlined.PushPin, null, tint = colors.textSecondary, modifier = Modifier.size(16.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(chat.lastMessageTime, fontSize = 14.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
            }
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    chat.lastMessagePreview,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    color = colors.textSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (chat.unreadCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(colors.border)
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(
                            if (chat.unreadCount > 999) "999+" else chat.unreadCount.toString(),
                            fontSize = 14.sp,
                            color = colors.textSecondary
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(
        color = colors.border.copy(alpha = 0.5f),
        thickness = 0.5.dp,
        modifier = Modifier.padding(start = 84.dp)
    )
}
