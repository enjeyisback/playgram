package app.playgram.feature.automation

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.playgram.core.design.*
import app.playgram.core.model.AutomationRule

private data class RuleTemplate(
    val icon: ImageVector,
    val title: String,
    val subtitle: String,
    val name: String,
    val regex: String,
    val dir: String
)

private val templates = listOf(
    RuleTemplate(
        Icons.Outlined.Movie, "Dubbed movies 1080p",
        "1080p or better videos whose name says dubbed or dual audio",
        "Dubbed movies 1080p", ".*(dubbed|dual).*(1080p|2160p).*", "/storage/emulated/0/Playgram/Movies"
    ),
    RuleTemplate(
        Icons.Outlined.VideoLibrary, "New episodes of a show",
        "Save new episodes of a series as they are posted",
        "New episodes", ".*S\\d{2}E\\d{2}.*", "/storage/emulated/0/Playgram/Series"
    ),
    RuleTemplate(
        Icons.Outlined.HighQuality, "4K UHD movies",
        "Anything labelled 4K, 2160p or UHD",
        "4K UHD movies", ".*(4K|2160p|UHD).*", "/storage/emulated/0/Playgram/Movies"
    )
)

@Composable
fun AutomationScreen(
    onBack: () -> Unit
) {
    app.playgram.core.design.KinoScaled { AutomationScreenContent(onBack = onBack) }
}

@Composable
private fun AutomationScreenContent(
    onBack: () -> Unit
) {
    val colors = LocalPlaygramColors.current

    val rules = remember { mutableStateListOf<AutomationRule>() }
    var mode by remember { mutableStateOf(0) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf<RuleTemplate?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .background(colors.background)
    ) {
        KinoTopBar(
            title = "Automation",
            subtitle = "Rules watch channels and save new posts",
            onBack = onBack,
            actions = {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(colors.surfaceVariant)
                        .clickable { draft = null; showCreateDialog = true }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Add, null, tint = colors.textPrimary, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Easy setup", fontSize = 15.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
                }
            }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item { KinoSegmented(listOf("Easy", "Advanced"), mode, { mode = it }) }

            if (mode == 0) {
                item {
                    KinoCard(container = Color(0xFF1B3556)) {
                        Row(verticalAlignment = Alignment.Top) {
                            Box(
                                Modifier.size(46.dp).clip(CircleShape).background(AccentBlue),
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Outlined.AutoAwesome, null, tint = Color.White) }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("Save new posts automatically", fontSize = 20.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
                                Text(
                                    "Three steps: pick channels, what to grab, and where to save it.",
                                    fontSize = 16.sp, fontWeight = FontWeight.Light, color = colors.textSecondary
                                )
                            }
                        }
                        KinoButton(
                            "Start Easy setup",
                            onClick = { draft = null; showCreateDialog = true },
                            modifier = Modifier.padding(top = 14.dp)
                        )
                    }
                }
                item {
                    Text(
                        "Templates",
                        fontSize = 17.sp, fontWeight = FontWeight.Light, color = colors.textSecondary,
                        modifier = Modifier.padding(start = 6.dp, top = 12.dp)
                    )
                }
                items(templates) { t ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(22.dp))
                            .background(colors.surfaceVariant)
                            .clickable { draft = t; showCreateDialog = true }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier.size(46.dp).clip(CircleShape).background(colors.surfaceElevated),
                            contentAlignment = Alignment.Center
                        ) { Icon(t.icon, null, tint = AccentBlueLight) }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(t.title, fontSize = 20.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
                            Text(t.subtitle, fontSize = 15.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
                        }
                    }
                }
            }

            if (rules.isNotEmpty() || mode == 1) {
                item {
                    Text(
                        if (rules.isEmpty()) "No rules yet" else "Your rules",
                        fontSize = 17.sp, fontWeight = FontWeight.Light, color = colors.textSecondary,
                        modifier = Modifier.padding(start = 6.dp, top = 8.dp)
                    )
                }
                items(rules, key = { it.id }) { rule ->
                    KinoCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(rule.name, fontSize = 18.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
                                Text("Match: ${rule.regexFilter}", fontSize = 13.sp, fontWeight = FontWeight.Light, color = AccentBlueLight)
                                Text("Save to: ${rule.targetDirectory}", fontSize = 13.sp, fontWeight = FontWeight.Light, color = colors.textSecondary)
                            }
                            Switch(
                                checked = rule.enabled,
                                onCheckedChange = {
                                    val i = rules.indexOf(rule)
                                    if (i != -1) rules[i] = rule.copy(enabled = it)
                                },
                                colors = SwitchDefaults.colors(checkedTrackColor = AccentBlue, checkedThumbColor = Color.White)
                            )
                        }
                        Row(
                            Modifier.fillMaxWidth().padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${rule.totalMatched} files saved", fontSize = 13.sp, color = colors.statusSuccess)
                            IconButton(onClick = { rules.remove(rule) }, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.Outlined.Delete, "Delete", tint = colors.textSecondary)
                            }
                        }
                    }
                }
            }

            item {
                Text(
                    "A rule watches channels for new posts and saves the ones that match. Rules run while Playgram is signed in, including in the background.",
                    fontSize = 16.sp, fontWeight = FontWeight.Light, color = colors.textSecondary,
                    modifier = Modifier.padding(top = 12.dp, start = 4.dp)
                )
            }
        }
    }

    if (showCreateDialog) {
        var ruleName by remember(draft) { mutableStateOf(draft?.name ?: "") }
        var regex by remember(draft) { mutableStateOf(draft?.regex ?: ".*(1080p|720p).*") }
        var targetDir by remember(draft) { mutableStateOf(draft?.dir ?: "/storage/emulated/0/Playgram") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("New rule", fontWeight = FontWeight.Light, color = colors.textPrimary) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(ruleName, { ruleName = it }, label = { Text("Name") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(regex, { regex = it }, label = { Text("Match (regex)") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(targetDir, { targetDir = it }, label = { Text("Save to") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (ruleName.isNotEmpty()) {
                            rules.add(
                                AutomationRule(
                                    id = "rule_${System.currentTimeMillis()}",
                                    name = ruleName,
                                    sourceChatId = -1001234567890L,
                                    targetDirectory = targetDir,
                                    regexFilter = regex,
                                    enabled = true
                                )
                            )
                        }
                        showCreateDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AccentBlue)
                ) { Text("Add rule") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) { Text("Cancel", color = colors.textSecondary) }
            },
            containerColor = colors.surfaceVariant
        )
    }
}
