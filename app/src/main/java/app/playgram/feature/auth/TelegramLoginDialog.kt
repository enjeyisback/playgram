package app.playgram.feature.auth

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import app.playgram.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.playgram.PlaygramApp
import app.playgram.core.design.*
import app.playgram.core.telegram.AuthStep

/** The login steps (keys fallback, phone, code, two-step password); shared by the full-screen page and the dialog. */
@Composable
fun LoginSteps(onSignedIn: () -> Unit) {
    val colors = LocalPlaygramColors.current
    val tg = PlaygramApp.instance.telegramManager

    val authStep by tg.authStep.collectAsState()
    val isLoading by tg.isLoading.collectAsState()
    val authError by tg.authError.collectAsState()
    val engineError by tg.engineError.collectAsState()
    val hasKeys by tg.credentialsSet.collectAsState()

    var apiId by remember { mutableStateOf("") }
    var apiHash by remember { mutableStateOf("") }
    var keyError by remember { mutableStateOf<String?>(null) }
    var phone by remember { mutableStateOf("+91 ") }
    var code by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(authStep) {
        if (authStep == AuthStep.AUTHENTICATED) onSignedIn()
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        val shownError = keyError ?: authError ?: engineError
        if (shownError != null) {
            Text(
                shownError,
                color = colors.statusError, fontSize = 14.sp, textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
            )
        }

        when {
            // Step 0: the user's own API keys
            !hasKeys -> {
                Text(
                    "Telegram needs API keys that belong to you. Get them for free at my.telegram.org → API development tools, then paste them here. They stay on this phone.",
                    fontSize = 14.sp, fontWeight = FontWeight.Light, color = colors.textSecondary,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = apiId, onValueChange = { apiId = it.filter(Char::isDigit) },
                    label = { Text("api_id") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = apiHash, onValueChange = { apiHash = it.trim() },
                    label = { Text("api_hash") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                KinoButton(
                    "Save keys",
                    onClick = { keyError = tg.saveCredentials(apiId, apiHash) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                )
            }

            // Step 1: phone number
            authStep == AuthStep.UNAUTHENTICATED || authStep == AuthStep.WAIT_PHONE -> {
                OutlinedTextField(
                    value = phone, onValueChange = { phone = it },
                    label = { Text("Phone number") }, placeholder = { Text("+91 98765 43210") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    modifier = Modifier.fillMaxWidth()
                )
                KinoButton(
                    if (isLoading) "Sending…" else "Send code",
                    enabled = !isLoading && authStep == AuthStep.WAIT_PHONE,
                    onClick = { tg.sendPhoneNumber(phone, {}, {}) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                )
                if (authStep == AuthStep.UNAUTHENTICATED) {
                    Text("Starting Telegram…", fontSize = 13.sp, color = colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
                }
            }

            // Step 2: login code
            authStep == AuthStep.WAIT_CODE -> {
                Text(
                    "Telegram sent a code to your Telegram app (or by SMS) for ${tg.pendingPhoneNumber}.",
                    fontSize = 15.sp, fontWeight = FontWeight.Light, color = colors.textPrimary,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = code, onValueChange = { code = it.filter(Char::isDigit).take(6) },
                    label = { Text("Login code") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                KinoButton(
                    if (isLoading) "Checking…" else "Log in",
                    enabled = !isLoading,
                    onClick = { tg.sendAuthCode(code, {}, {}) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                )
                TextButton(onClick = { code = ""; tg.restartLogin() }) {
                    Text("Use a different number", color = AccentBlueLight)
                }
            }

            // Step 3: two-step verification
            authStep == AuthStep.WAIT_PASSWORD -> {
                Text(
                    "This account has two-step verification. Enter your Telegram password.",
                    fontSize = 15.sp, fontWeight = FontWeight.Light, color = colors.textPrimary,
                    textAlign = TextAlign.Center, modifier = Modifier.padding(bottom = 12.dp)
                )
                OutlinedTextField(
                    value = password, onValueChange = { password = it },
                    label = { Text("Password") }, singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    modifier = Modifier.fillMaxWidth()
                )
                KinoButton(
                    if (isLoading) "Checking…" else "Continue",
                    enabled = !isLoading,
                    onClick = { tg.sendPassword(password, {}, {}) },
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp)
                )
            }

            else -> Text("Signed in.", color = colors.textPrimary)
        }

    }
}

/** Full-screen sign-in shown on launch until a Telegram account is connected. */
@Composable
fun LoginScreen() {
    val colors = LocalPlaygramColors.current
    Box(Modifier.fillMaxSize().background(colors.background)) {
        KinoScaled {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()
                    .verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(painterResource(R.drawable.logo_mark), null, Modifier.size(84.dp))
                Spacer(Modifier.height(14.dp))
                Text(
                    buildAnnotatedString {
                        withStyle(SpanStyle(color = colors.textPrimary)) { append("Play") }
                        withStyle(SpanStyle(color = Color(0xFF9B4DFF))) { append("gram") }
                    },
                    fontSize = 32.sp, fontWeight = FontWeight.Bold,
                    fontFamily = androidx.compose.ui.text.font.FontFamily.SansSerif
                )
                Text(
                    "Sign in with your phone number to see your Telegram channels and videos.",
                    fontSize = 15.sp, fontWeight = FontWeight.Light, color = colors.textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 8.dp, bottom = 28.dp)
                )
                LoginSteps(onSignedIn = {})
            }
        }
    }
}

@Composable
fun TelegramLoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: () -> Unit
) {
    val colors = LocalPlaygramColors.current
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            shape = RoundedCornerShape(26.dp),
            color = colors.surfaceVariant
        ) {
            KinoScaled {
                Column(
                    Modifier.verticalScroll(rememberScrollState()).padding(22.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Connect Telegram", fontSize = 24.sp, fontWeight = FontWeight.Light, color = colors.textPrimary)
                    Spacer(Modifier.height(14.dp))
                    LoginSteps(onSignedIn = { onLoginSuccess(); onDismiss() })
                }
            }
        }
    }
}
