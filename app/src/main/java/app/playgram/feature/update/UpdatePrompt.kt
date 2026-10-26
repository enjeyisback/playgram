package app.playgram.feature.update

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.playgram.core.design.AccentBlueLight
import app.playgram.core.design.LocalPlaygramColors
import app.playgram.core.update.UpdateManager
import app.playgram.core.update.UpdateState

/** "Update available" dialog, shown over any screen once a newer GitHub release is found. */
@Composable
fun UpdatePrompt() {
    val colors = LocalPlaygramColors.current
    val um = UpdateManager.getInstance(LocalContext.current)
    val state by um.state.collectAsState()
    val dismissed by um.dialogDismissed.collectAsState()
    val info = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        else -> null
    }
    if (info == null || dismissed) return
    val downloading = state as? UpdateState.Downloading

    AlertDialog(
        onDismissRequest = { if (downloading == null) um.dismissDialog() },
        title = { Text("Update available · v${info.version}", fontWeight = FontWeight.Light, color = colors.textPrimary) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    info.notes.ifBlank { "A new version of Playgram is ready." }.take(800),
                    fontSize = 15.sp, color = colors.textSecondary
                )
                if (downloading != null) {
                    LinearProgressIndicator(
                        progress = { downloading.progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).height(4.dp),
                        color = AccentBlueLight, trackColor = colors.surface
                    )
                    Text("Downloading ${(downloading.progress * 100).toInt()}%", fontSize = 13.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { um.installDownloaded(info) }, enabled = downloading == null) {
                Text(if (downloading == null) "Update now" else "Downloading…", color = AccentBlueLight)
            }
        },
        dismissButton = {
            if (downloading == null) TextButton(onClick = { um.dismissDialog() }) { Text("Later", color = colors.textSecondary) }
        },
        containerColor = colors.surfaceVariant
    )
}
