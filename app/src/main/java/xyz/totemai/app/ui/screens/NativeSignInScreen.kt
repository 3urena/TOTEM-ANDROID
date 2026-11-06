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
import androidx.compose.foundation.layout.size
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
import xyz.totemai.app.ui.theme.AppBackground
import xyz.totemai.app.ui.theme.InkElevated
import xyz.totemai.app.ui.theme.TextPrimary
import xyz.totemai.app.ui.theme.TextSecondary
import xyz.totemai.app.ui.theme.Violet

private enum class SignInPage {
    LOGIN,
    SIGN_UP,
    RECOVER,
    RESET
}

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
    var canSignUp by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var page by remember { mutableStateOf(SignInPage.LOGIN) }

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var signUpUsername by remember { mutableStateOf("") }
    var signUpPassword by remember { mutableStateOf("") }
    var recoveryUsername by remember { mutableStateOf("") }
    var recoveryCodeOne by remember { mutableStateOf("") }
    var recoveryCodeTwo by remember { mutableStateOf("") }
    var resetPassword by remember { mutableStateOf("") }
    var confirmResetPassword by remember { mutableStateOf("") }
    var resetToken by remember { mutableStateOf<String?>(null) }

    var error by remember { mutableStateOf<String?>(null) }
    var successMessage by remember { mutableStateOf<String?>(null) }
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

    fun loadAuthSettings() {
        scope.launch {
            configLoading = true
            error = null
            try {
                authConfig = api.authConfig()
                canSignUp = runCatching { api.publicRegistrationEnabled() }.getOrDefault(false)
            } catch (e: Exception) {
                error = e.message ?: "Could not load sign-in settings."
            } finally {
                configLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            authConfig = api.authConfig()
            canSignUp = runCatching { api.publicRegistrationEnabled() }.getOrDefault(false)
            val config = authConfig
            if (config != null && !config.multiUser && !config.requiresAuth) {
                loading = true
                applyLoginResult(api.signIn(username = "", password = "", multiUser = false))
                loading = false
            }
        } catch (e: Exception) {
            error = e.message ?: "Could not reach the TOTEM sign-in service."
        } finally {
            configLoading = false
        }
    }

    fun submitLogin() {
        val config = authConfig ?: return
        if (loading) return
        if (config.multiUser && username.isBlank()) {
            error = "Enter your username or email."
            return
        }
        if ((config.requiresAuth || config.multiUser) && password.isBlank()) {
            error = "Enter your password."
            return
        }
        scope.launch {
            loading = true
            error = null
            successMessage = null
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

    fun submitRegistration() {
        if (loading) return
        if (signUpUsername.isBlank() || email.isBlank() || signUpPassword.length < 8) {
            error = "Enter a username, valid email and password with at least 8 characters."
            return
        }
        scope.launch {
            loading = true
            error = null
            successMessage = null
            try {
                api.registerUser(signUpUsername.trim(), email.trim(), signUpPassword)
                username = signUpUsername.trim()
                password = ""
                page = SignInPage.LOGIN
                successMessage = "Account created. Sign in with your username or email."
            } catch (e: Exception) {
                error = e.message ?: "Could not create the account."
            } finally {
                loading = false
            }
        }
    }

    fun submitRecovery() {
        if (loading) return
        if (recoveryUsername.isBlank() || recoveryCodeOne.isBlank() || recoveryCodeTwo.isBlank()) {
            error = "Enter your username and both recovery codes."
            return
        }
        scope.launch {
            loading = true
            error = null
            try {
                resetToken = api.recoverAccount(
                    recoveryUsername.trim(),
                    listOf(recoveryCodeOne.trim(), recoveryCodeTwo.trim())
                )
                page = SignInPage.RESET
            } catch (e: Exception) {
                error = e.message ?: "Account recovery failed."
            } finally {
                loading = false
            }
        }
    }

    fun submitPasswordReset() {
        val token = resetToken ?: return
        if (loading) return
        if (resetPassword.length < 8) {
            error = "Your new password must contain at least 8 characters."
            return
        }
        if (resetPassword != confirmResetPassword) {
            error = "The passwords do not match."
            return
        }
        scope.launch {
            loading = true
            error = null
            try {
                api.resetPassword(token, resetPassword, confirmResetPassword)
                resetToken = null
                resetPassword = ""
                confirmResetPassword = ""
                password = ""
                page = SignInPage.LOGIN
                successMessage = "Password reset successfully. Sign in with your new password."
            } catch (e: Exception) {
                error = e.message ?: "Could not reset your password."
            } finally {
                loading = false
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppBackground)
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

            when (page) {
                SignInPage.LOGIN -> {
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
                            TextButton(onClick = { loadAuthSettings() }) {
                                Text("Try again", color = Violet)
                            }
                        }
                        else -> {
                            if (authConfig?.multiUser == true) {
                                SignInField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = "Username or email",
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next
                                )
                                Spacer(Modifier.height(14.dp))
                            }

                            if (authConfig?.requiresAuth == true || authConfig?.multiUser == true) {
                                SignInField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = "Password",
                                    password = true,
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done
                                )
                                Spacer(Modifier.height(18.dp))
                            }

                            Button(
                                onClick = { submitLogin() },
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
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp
                                    )
                                } else {
                                    Text("SIGN IN", fontWeight = FontWeight.Bold)
                                }
                            }

                            if (authConfig?.multiUser == true) {
                                Spacer(Modifier.height(6.dp))
                                TextButton(
                                    onClick = {
                                        error = null
                                        successMessage = null
                                        page = SignInPage.RECOVER
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Forgot password? Reset with recovery codes", color = Violet)
                                }
                            }

                            if (canSignUp) {
                                TextButton(
                                    onClick = {
                                        error = null
                                        successMessage = null
                                        page = SignInPage.SIGN_UP
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Create a new account", color = Violet)
                                }
                            }
                        }
                    }

                    if (!successMessage.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = successMessage.orEmpty(),
                            color = Color(0xFF8FE0A0),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                    if (!error.isNullOrBlank() && authConfig != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = error.orEmpty(),
                            color = Color(0xFFFF8585),
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                SignInPage.SIGN_UP -> {
                    AuthPageTitle(
                        title = "CREATE ACCOUNT",
                        description = "Create a TOTEM account with your username and email"
                    )
                    SignInField(
                        value = signUpUsername,
                        onValueChange = { signUpUsername = it },
                        label = "Username",
                        imeAction = ImeAction.Next
                    )
                    Spacer(Modifier.height(12.dp))
                    SignInField(
                        value = email,
                        onValueChange = { email = it },
                        label = "Email",
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    )
                    Spacer(Modifier.height(12.dp))
                    SignInField(
                        value = signUpPassword,
                        onValueChange = { signUpPassword = it },
                        label = "Password (at least 8 characters)",
                        password = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                    Spacer(Modifier.height(18.dp))
                    AuthActionButton("CREATE ACCOUNT", loading) { submitRegistration() }
                    TextButton(
                        onClick = {
                            error = null
                            page = SignInPage.LOGIN
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Back to sign in", color = Violet) }
                    AuthError(error)
                }

                SignInPage.RECOVER -> {
                    AuthPageTitle(
                        title = "RESET PASSWORD",
                        description = "Enter your username and the two recovery codes saved when you first signed in"
                    )
                    SignInField(
                        value = recoveryUsername,
                        onValueChange = { recoveryUsername = it },
                        label = "Username",
                        imeAction = ImeAction.Next
                    )
                    Spacer(Modifier.height(12.dp))
                    SignInField(
                        value = recoveryCodeOne,
                        onValueChange = { recoveryCodeOne = it },
                        label = "Recovery code 1",
                        imeAction = ImeAction.Next
                    )
                    Spacer(Modifier.height(12.dp))
                    SignInField(
                        value = recoveryCodeTwo,
                        onValueChange = { recoveryCodeTwo = it },
                        label = "Recovery code 2",
                        imeAction = ImeAction.Done
                    )
                    Spacer(Modifier.height(18.dp))
                    AuthActionButton("CONTINUE", loading) { submitRecovery() }
                    TextButton(
                        onClick = {
                            error = null
                            page = SignInPage.LOGIN
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Back to sign in", color = Violet) }
                    AuthError(error)
                }

                SignInPage.RESET -> {
                    AuthPageTitle(
                        title = "CHOOSE A NEW PASSWORD",
                        description = "Enter and confirm your new password"
                    )
                    SignInField(
                        value = resetPassword,
                        onValueChange = { resetPassword = it },
                        label = "New password",
                        password = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Next
                    )
                    Spacer(Modifier.height(12.dp))
                    SignInField(
                        value = confirmResetPassword,
                        onValueChange = { confirmResetPassword = it },
                        label = "Confirm new password",
                        password = true,
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done
                    )
                    Spacer(Modifier.height(18.dp))
                    AuthActionButton("SAVE NEW PASSWORD", loading) { submitPasswordReset() }
                    TextButton(
                        onClick = {
                            error = null
                            resetToken = null
                            page = SignInPage.LOGIN
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Cancel", color = Violet) }
                    AuthError(error)
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

@Composable
private fun AuthPageTitle(title: String, description: String) {
    Text(
        text = title,
        color = TextPrimary,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(7.dp))
    Text(
        text = description,
        color = TextSecondary,
        style = MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center
    )
    Spacer(Modifier.height(26.dp))
}

@Composable
private fun SignInField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    password: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Next
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        label = { Text(label) },
        visualTransformation = if (password) PasswordVisualTransformation()
            else androidx.compose.ui.text.input.VisualTransformation.None,
        keyboardOptions = KeyboardOptions(
            keyboardType = keyboardType,
            imeAction = imeAction
        )
    )
}

@Composable
private fun AuthActionButton(
    label: String,
    loading: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
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
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp
            )
        } else {
            Text(label, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun AuthError(error: String?) {
    if (!error.isNullOrBlank()) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = error,
            color = Color(0xFFFF8585),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
