package app.playgram.feature.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.playgram.PlaygramApp
import app.playgram.core.design.LocalPlaygramColors

@Composable
fun AppLockScreen(
    onUnlocked: () -> Unit
) {
    val colors = LocalPlaygramColors.current
    val appLockManager = PlaygramApp.instance.appLockManager

    var enteredPin by remember { mutableStateOf("") }
    var isError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = CircleShape,
            color = colors.accent.copy(alpha = 0.2f),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Lock, contentDescription = null, tint = colors.accent, modifier = Modifier.size(36.dp))
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        Text("Playgram Locked", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = colors.textPrimary)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            if (isError) "Incorrect PIN. Try again." else "Enter your 4-digit security PIN",
            fontSize = 13.sp,
            color = if (isError) colors.statusError else colors.textSecondary
        )

        Spacer(modifier = Modifier.height(30.dp))

        // 4 Dot indicators
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            repeat(4) { idx ->
                val filled = idx < enteredPin.length
                Surface(
                    shape = CircleShape,
                    color = if (filled) colors.accent else colors.surfaceElevated,
                    modifier = Modifier.size(16.dp)
                ) {}
            }
        }

        Spacer(modifier = Modifier.height(40.dp))

        // Numeric Keypad
        val numpad = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("", "0", "DEL")
        )

        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            numpad.forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    row.forEach { key ->
                        if (key.isEmpty()) {
                            Spacer(modifier = Modifier.size(68.dp))
                        } else if (key == "DEL") {
                            Surface(
                                shape = CircleShape,
                                color = colors.surfaceVariant,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clickable {
                                        if (enteredPin.isNotEmpty()) {
                                            enteredPin = enteredPin.dropLast(1)
                                            isError = false
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(Icons.Default.Backspace, contentDescription = "Delete", tint = colors.textPrimary)
                                }
                            }
                        } else {
                            Surface(
                                shape = CircleShape,
                                color = colors.surfaceVariant,
                                modifier = Modifier
                                    .size(68.dp)
                                    .clickable {
                                        if (enteredPin.length < 4) {
                                            val next = enteredPin + key
                                            enteredPin = next
                                            if (next.length == 4) {
                                                if (appLockManager.verifyPin(next)) {
                                                    onUnlocked()
                                                } else {
                                                    isError = true
                                                    enteredPin = ""
                                                }
                                            }
                                        }
                                    }
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(key, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = colors.textPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
