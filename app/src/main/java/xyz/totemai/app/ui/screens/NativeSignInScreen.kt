package xyz.totemai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import xyz.totemai.app.SessionStore
import xyz.totemai.app.TotemApi
import xyz.totemai.app.TotemLoginResult
import xyz.totemai.app.ui.theme.Ink
import xyz.totemai.app.ui.theme.InkElevated
import xyz.totemai.app.ui.theme.Outline
import xyz.totemai.app.ui.theme.TextPrimary
import xyz.totemai.app.ui.theme.TextSecondary
import xyz.totemai.app.ui.theme.Violet

@Composable
fun NativeSignInScreen(
    store: SessionStore,
    onSignedIn: () -> Unit
) {
    val api = remember(store) { TotemApi(store) }
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current

    var authConfig by remember { mutableStateOf<TotemApi.AuthConfig?>(null) }
    var configLoading by remember { mutableStateOf(true) }
    var loading by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var recoveryCodes by remember { mutableStateOf<List<String>>(emptyList()) }
    var pendingToken by remember { mutableStateOf<String?>(null) }
    var pendingUser by remember { mutableStateOf<String?>(null) }

    fun finishSignIn() {
        val token = pendingToken ?: return
        store.save(token, pendingUser)
        pendingToken = null
        pendingUser = null
        recoveryCodes = emptyList()
        onSignedIn()
    }

    fun applyLoginResult(result: TotemLoginResult) {
        if (!result.valid || result.token.isNullOrBlank()) {
            error = result.message ?: "Login failed. Please check your credentials."
            return
        }
        if (result.recoveryCodes.isNotEmpty()) {
            pendingToken = result.token
            pendingUser = result.userJson
            recoveryCodes = result.recoveryCodes
        } else {
            store.save(result.token, result.userJson)
            onSignedIn()
        }
    }

    LaunchedEffect(Unit) {
        try {
            val config = api.authConfig()
            authConfig = config
            if (!config.multiUser && !config.requiresAuth) {
                loading = true
                val result = api.signIn(username = "", password = "", multiUser = false)
                applyLoginResult(result)
                loading = false
            }
        } catch (e: Exception) {
            error = e.message ?: "Could not reach the TOTEM sign-in service."
        } finally {
            configLoading = false
        }
    }

    fun submit() {
        val config = authConfig ?: return
        if (loading) return
        if (config.multiUser && username.isBlank()) {
            error = "Enter your username or email."
            return
        }
        if (config.requiresAuth && password.isBlank()) {
            error = "Enter your password."
            return
        }
        scope.launch {
            loading = true
            error = null
            try {
                applyLoginResult(
                    api.signIn(
                        username = username.trim(),
                        password = password,
                        multiUser = config.multiUser
                    )
                )
            } catch (e: Exception) {
                error = e.message ?: "Could not sign in to TOTEM."
            } finally {
                loading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(22.dp))
                    .background(Violet.copy(alpha = 0.16f))
                    .padding(horizontal = 22.dp, vertical = 14.dp)
            ) {
                Text(
                    text = "TOTEM",
                    color = Violet,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 3.sp
                )
            }
            Spacer(Modifier.height(22.dp))
            Text(
                text = "WELCOME BACK",
                color = TextPrimary,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(7.dp))
            Text(
                text = "Sign in securely to your TOTEM account",
                color = TextSecondary,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(30.dp))

            when {
                configLoading -> CircularProgressIndicator(color = Violet)
                authConfig == null -> {
                    Text(
                        text = error ?: "The sign-in service is unavailable.",
                        color = Color(0xFFFF8585),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(14.dp))
                    TextButton(onClick = {
                        configLoading = true
                        error = null
                        scope.launch {
                            try {
                                authConfig = api.authConfig()
                            } catch (e: Exception) {
                                error = e.message ?: "Could not load sign-in settings."
                            } finally {
                                configLoading = false
                            }
                        }
                    }) { Text("Try again", color = Violet) }
                }
                else -> {
                    if (authConfig?.multiUser == true) {
                        OutlinedTextField(
                            value = username,
                            onValueChange = { username = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Username or email") },
                            placeholder = { Text("Enter your username or email") },
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Email,
                                imeAction = ImeAction.Next
                            )
                        )
                        Spacer(Modifier.height(14.dp))
                    }

                    if (authConfig?.requiresAuth == true || authConfig?.multiUser == true) {
                        OutlinedTextField(
                            value = password,
                            onValueChange = { password = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("Password") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            )
                        )
                        Spacer(Modifier.height(18.dp))
                    }

                    if (!error.isNullOrBlank()) {
                        Text(
                            text = error.orEmpty(),
                            color = Color(0xFFFF8585),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    Button(
                        onClick = { submit() },
                        enabled = !loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(15.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Violet,
                            contentColor = Color.White
                        )
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.height(20.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("SIGN IN", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = "Connected directly to the official TOTEM service",
                color = TextSecondary,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center
            )
        }
    }

    if (recoveryCodes.isNotEmpty()) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Save your recovery codes") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("These codes are shown only once. Save them somewhere private before continuing.")
                    Text(
                        text = recoveryCodes.joinToString("\n"),
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(recoveryCodes.joinToString("\n")))
                        }
                    ) {
                        Text("Copy recovery codes", color = Violet)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { finishSignIn() }) {
                    Text("I saved them", color = Violet)
                }
            },
            containerColor = InkElevated,
            titleContentColor = TextPrimary,
            textContentColor = TextSecondary
        )
    }
}
