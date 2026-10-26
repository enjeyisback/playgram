package app.playgram.feature.pro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.playgram.core.design.LocalPlaygramColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProScreen(
    onBack: () -> Unit
) {
    val colors = LocalPlaygramColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playgram Pro", fontWeight = FontWeight.Bold, color = colors.textPrimary) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = colors.textPrimary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = colors.background)
            )
        },
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = colors.surfaceElevated
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Button(
                        onClick = onBack,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = colors.accent),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Text("Active (Clean Rebuild Edition)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        },
        containerColor = colors.background
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = colors.surfaceVariant
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = colors.accent, modifier = Modifier.size(48.dp))
                        Spacer(modifier = Modifier.height(10.dp))
                        Text("PLAYGRAM UNLIMITED", fontWeight = FontWeight.Black, fontSize = 18.sp, color = colors.textPrimary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Full unrestricted media suite unlocked", fontSize = 12.sp, color = colors.textSecondary)
                    }
                }
            }

            item {
                ProFeatureRow(
                    icon = Icons.Default.Speed,
                    title = "Multi-Threaded Turbo Downloader",
                    desc = "No throttle on chunk streaming, parallel segment fetching."
                )
            }
            item {
                ProFeatureRow(
                    icon = Icons.Default.Audiotrack,
                    title = "Dual-Audio Mixer & Subtitle Sync",
                    desc = "Live balance blending and microsecond audio offset delay adjustment."
                )
            }
            item {
                ProFeatureRow(
                    icon = Icons.Default.Tv,
                    title = "Full Android TV & Leanback Support",
                    desc = "Seamless D-Pad navigation, 4K HDR playback and Google Cast output."
                )
            }
            item {
                ProFeatureRow(
                    icon = Icons.Default.Shield,
                    title = "Private Vault & Biometric Lock",
                    desc = "Hardware backed keystore encryption and FLAG_SECURE protection."
                )
            }
        }
    }
}

@Composable
fun ProFeatureRow(icon: ImageVector, title: String, desc: String) {
    val colors = LocalPlaygramColors.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = colors.surfaceVariant
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = colors.accent, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = colors.textPrimary)
                Spacer(modifier = Modifier.height(2.dp))
                Text(desc, fontSize = 12.sp, color = colors.textSecondary)
            }
        }
    }
}
