package app.playgram.feature.devices

import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.playgram.core.design.AccentBlue
import app.playgram.core.design.AccentBlueLight
import app.playgram.core.design.LocalPlaygramColors
import app.playgram.core.model.DeviceNode
import app.playgram.core.model.DeviceType

private const val GB = 1024.0 * 1024.0 * 1024.0

@Composable
fun DevicesScreen(
    onNavigate: (String) -> Unit
) {
    val d = androidx.compose.ui.platform.LocalDensity.current
    androidx.compose.runtime.CompositionLocalProvider(
        androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * 0.88f)
    ) { DevicesScreenContent(onNavigate) }
}

@Composable
private fun DevicesScreenContent(
    onNavigate: (String) -> Unit
) {
    val colors = LocalPlaygramColors.current
    val context = LocalContext.current

    val (freeGb, totalGb) = remember {
        try {
            val st = StatFs(Environment.getExternalStorageDirectory().path)
            st.availableBytes / GB to st.totalBytes / GB
        } catch (e: Exception) { 0.0 to 0.0 }
    }
    val cacheMb = remember {
        (context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } / (1024.0 * 1024.0)).toInt()
    }

    val devices = remember { mutableStateListOf<DeviceNode>() }
    var showAddDialog by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Devices", fontSize = 30.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
                    Text(
                        "Looking for phones and TVs on this network…",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Light,
                        color = colors.textSecondary
                    )
                }
                IconButton(onClick = {}) {
                    Icon(Icons.Outlined.Refresh, "Refresh", tint = colors.textPrimary)
                }
            }
            SectionLabel("This phone", Modifier.padding(top = 22.dp, bottom = 10.dp))
            Card {
                Row(verticalAlignment = Alignment.Top) {
                    Icon(Icons.Outlined.PhoneAndroid, null, tint = AccentBlueLight, modifier = Modifier.padding(top = 4.dp))
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Internal · This phone", fontSize = 18.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
                        Text(
                            String.format("%.1f GB free of %.0f GB · Cache · streaming · small files", freeGb, totalGb),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Light,
                            color = colors.textSecondary
                        )
                    }
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AccentBlue)
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) { Text("DEFAULT", fontSize = 12.sp, color = Color.White) }
                }
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { if (totalGb > 0) (1 - freeGb / totalGb).toFloat() else 0f },
                    modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                    color = AccentBlueLight,
                    trackColor = colors.border
                )
                StorageRow(Icons.Outlined.SdCard, "SD card · Not set")
                StorageRow(Icons.Outlined.Usb, "USB · Not set")
                Text(
                    "What Playgram keeps where",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                    color = colors.textPrimary,
                    modifier = Modifier.padding(top = 18.dp)
                )
                Text("No saved downloads yet", fontSize = 14.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Streaming cache", fontSize = 18.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
                    Text("$cacheMb MB", fontSize = 18.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
                }
            }

            SectionLabel("NAS shares", Modifier.padding(top = 24.dp, bottom = 10.dp), pro = true)
        }

        if (devices.isEmpty()) {
            item {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Dns, null, tint = AccentBlueLight)
                        Spacer(Modifier.width(12.dp))
                        Text("No NAS yet", fontSize = 22.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
                    }
                    Text(
                        "Save downloads to SMB, WebDAV, SFTP or FTP shares, and play or copy files from them.",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Light,
                        color = colors.textSecondary,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        modifier = Modifier
                            .padding(top = 16.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AccentBlue)
                            .clickable { showAddDialog = true }
                            .padding(horizontal = 22.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Outlined.Add, null, tint = Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text("Add NAS", fontSize = 17.sp, color = Color.White)
                    }
                }
            }
        } else {
            items(devices.size) { i ->
                val d = devices[i]
                Box(Modifier.padding(bottom = 10.dp)) {
                    Card {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                when (d.type) {
                                    DeviceType.CAST -> Icons.Outlined.Tv
                                    DeviceType.SFTP -> Icons.Outlined.Terminal
                                    else -> Icons.Outlined.Dns
                                },
                                null,
                                tint = AccentBlueLight
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(d.name, fontSize = 17.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
                                Text(
                                    "${d.type.name} · ${d.host}:${d.port}",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Light,
                                    color = colors.textSecondary
                                )
                            }
                            IconButton(onClick = { devices.removeAt(i) }) {
                                Icon(Icons.Outlined.Close, "Remove", tint = colors.textSecondary)
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "+ Add NAS",
                    fontSize = 16.sp,
                    color = AccentBlueLight,
                    modifier = Modifier.clickable { showAddDialog = true }.padding(vertical = 8.dp)
                )
            }
        }

        item {
            SectionLabel("Phones & TVs", Modifier.padding(top = 24.dp, bottom = 10.dp), pro = true)
            Card {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.PhoneAndroid, null, tint = AccentBlueLight)
                    Spacer(Modifier.width(12.dp))
                    Text("No paired device yet", fontSize = 22.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
                }
            }
        }
    }

    if (showAddDialog) {
        var name by remember { mutableStateOf("") }
        var host by remember { mutableStateOf("") }
        var port by remember { mutableStateOf("445") }
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add NAS", fontWeight = FontWeight.Light, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(host, { host = it }, label = { Text("Host / IP address") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(port, { port = it }, label = { Text("Port") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (name.isNotEmpty() && host.isNotEmpty()) {
                            devices.add(
                                DeviceNode(
                                    id = "dev_${System.currentTimeMillis()}",
                                    name = name,
                                    type = DeviceType.SMB,
                                    host = host,
                                    port = port.toIntOrNull() ?: 445,
                                    isConnected = false
                                )
                            )
                        }
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel", color = colors.textSecondary) }
            },
            containerColor = colors.surfaceVariant
        )
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier, pro: Boolean = false) {
    val colors = LocalPlaygramColors.current
    Row(modifier.padding(start = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(text, fontSize = 17.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
        if (pro) {
            Spacer(Modifier.width(12.dp))
            Row(
                Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1B2A3E))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Outlined.WorkspacePremium, null, tint = AccentBlueLight, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text("PRO", fontSize = 13.sp, color = AccentBlueLight)
            }
            Spacer(Modifier.width(10.dp))
            Text("with Playgram Pro", fontSize = 14.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
        }
    }
}

@Composable
private fun Card(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(LocalPlaygramColors.current.surfaceVariant)
            .padding(18.dp),
        content = content
    )
}

@Composable
private fun StorageRow(icon: ImageVector, title: String) {
    val colors = LocalPlaygramColors.current
    HorizontalDivider(color = colors.border.copy(alpha = 0.5f), modifier = Modifier.padding(top = 14.dp))
    Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, null, tint = colors.textSecondary, modifier = Modifier.padding(top = 2.dp))
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
            Text(
                "Not set · choose a folder in Storage & settings",
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                color = colors.textSecondary
            )
        }
    }
}
