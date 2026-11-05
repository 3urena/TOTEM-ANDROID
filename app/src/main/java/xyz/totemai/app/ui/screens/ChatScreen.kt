package xyz.totemai.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import xyz.totemai.app.SessionStore
import xyz.totemai.app.api.TotemApi
import xyz.totemai.app.api.TotemApiException
import xyz.totemai.app.api.TotemChatRecord
import xyz.totemai.app.api.TotemThread
import xyz.totemai.app.api.TotemWorkspace
import xyz.totemai.app.api.TotemWorkspaceContent
import xyz.totemai.app.ui.theme.Danger
import xyz.totemai.app.ui.theme.Ink
import xyz.totemai.app.ui.theme.InkElevated
import xyz.totemai.app.ui.theme.Outline
import xyz.totemai.app.ui.theme.Surface1
import xyz.totemai.app.ui.theme.TextPrimary
import xyz.totemai.app.ui.theme.TextSecondary
import xyz.totemai.app.ui.theme.Violet

private data class ChatUiMessage(
    val id: String,
    val isUser: Boolean,
    val text: String,
    val isError: Boolean = false
)

@Composable
fun ChatScreen(
    store: SessionStore,
    signedIn: Boolean,
    sessionVersion: Int,
    onSessionChanged: () -> Unit
) {
    val api = remember(store) { TotemApi(store) }
    val scope = rememberCoroutineScope()
    val drawerState = androidx.compose.material3.rememberDrawerState(
        androidx.compose.material3.DrawerValue.Closed
    )
    val clipboard = LocalClipboardManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()

    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var isSigningIn by remember { mutableStateOf(false) }
    var recoveryCodes by remember { mutableStateOf<List<String>?>(null) }

    var isLoading by remember { mutableStateOf(false) }
    var isConnecting by remember { mutableStateOf(false) }
    var needsDeviceConnection by remember { mutableStateOf(false) }
    var isReady by remember { mutableStateOf(false) }
    var isSending by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var workspace by remember { mutableStateOf<TotemWorkspace?>(null) }
    var modelTag by remember { mutableStateOf("TOTEM AI") }
    var threads by remember { mutableStateOf<List<TotemThread>>(emptyList()) }
    var history by remember { mutableStateOf<TotemWorkspaceContent?>(null) }
    var selectedThread by remember { mutableStateOf<TotemThread?>(null) }
    var draft by remember { mutableStateOf("") }
    val messages = remember { mutableStateListOf<ChatUiMessage>() }

    fun showHistory(content: TotemWorkspaceContent, thread: TotemThread) {
        history = content
        threads = content.threads.sortedByDescending { it.lastUpdatedAt }
        selectedThread = content.threads.firstOrNull { it.slug == thread.slug } ?: thread
        messages.clear()
        content.chats
            .filter { it.threadId == thread.id }
            .sortedBy { it.id }
            .forEach { record ->
                if (record.prompt.isNotBlank()) {
                    messages.add(
                        ChatUiMessage(
                            id = "user-${record.id}",
                            isUser = true,
                            text = record.prompt
                        )
                    )
                }
                if (record.response.isNotBlank()) {
                    messages.add(
                        ChatUiMessage(
                            id = "assistant-${record.id}",
                            isUser = false,
                            text = record.response
                        )
                    )
                }
            }
    }

    suspend fun loadChatFromStart() {
        val available = api.workspaces()
        val selectedWorkspace = available.firstOrNull()
            ?: throw TotemApiException(404, "No workspace is available for this account.")
        workspace = selectedWorkspace
        modelTag = api.modelTag(selectedWorkspace.slug) ?: "TOTEM AI"

        // A fresh thread is created each time the app starts its Chat screen.
        // Previous conversations remain available in the hamburger history.
        val freshThread = api.createThread(selectedWorkspace.slug)
        val content = api.workspaceContent(selectedWorkspace.slug)
        history = content
        threads = content.threads.sortedByDescending { it.lastUpdatedAt }
        selectedThread = content.threads.firstOrNull { it.slug == freshThread.slug } ?: freshThread
        messages.clear()
        isReady = true
    }

    suspend fun refreshSelectedThread() {
        val activeWorkspace = workspace ?: return
        val activeThread = selectedThread ?: return
        val content = api.workspaceContent(activeWorkspace.slug)
        showHistory(content, activeThread)
    }

    suspend fun openThread(thread: TotemThread) {
        val activeWorkspace = workspace ?: return
        val content = api.workspaceContent(activeWorkspace.slug)
        showHistory(content, thread)
    }

    suspend fun createNewThread() {
        val activeWorkspace = workspace ?: return
        val thread = api.createThread(activeWorkspace.slug)
        val content = api.workspaceContent(activeWorkspace.slug)
        history = content
        threads = content.threads.sortedByDescending { it.lastUpdatedAt }
        selectedThread = content.threads.firstOrNull { it.slug == thread.slug } ?: thread
        messages.clear()
    }

    LaunchedEffect(signedIn, sessionVersion) {
        if (!signedIn) {
            isReady = false
            needsDeviceConnection = false
            isLoading = false
            errorMessage = null
            workspace = null
            selectedThread = null
            messages.clear()
            return@LaunchedEffect
        }

        errorMessage = null
        isReady = false
        if (store.mobileDeviceToken().isNullOrBlank()) {
            needsDeviceConnection = true
            return@LaunchedEffect
        }

        isLoading = true
        try {
            api.ensureMobileDevice()
            loadChatFromStart()
            needsDeviceConnection = false
        } catch (error: Exception) {
            errorMessage = error.message ?: "Could not connect to TOTEM."
        } finally {
            isLoading = false
        }
    }

    fun connectDevice() {
        scope.launch {
            isConnecting = true
            errorMessage = null
            try {
                api.ensureMobileDevice()
                needsDeviceConnection = false
                isLoading = true
                loadChatFromStart()
            } catch (error: Exception) {
                errorMessage = error.message ?: "Device connection failed."
                needsDeviceConnection = store.mobileDeviceToken().isNullOrBlank()
            } finally {
                isLoading = false
                isConnecting = false
            }
        }
    }

    fun signIn() {
        if (isSigningIn || password.isBlank()) return
        scope.launch {
            isSigningIn = true
            errorMessage = null
            try {
                val result = api.login(identifier, password)
                store.save(result.token, result.userJson)
                password = ""
                recoveryCodes = result.recoveryCodes.takeIf { it.isNotEmpty() }
                onSessionChanged()
            } catch (error: Exception) {
                errorMessage = error.message ?: "Sign-in failed."
            } finally {
                isSigningIn = false
            }
        }
    }

    fun refreshChat() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                refreshSelectedThread()
            } catch (error: Exception) {
                errorMessage = error.message ?: "Could not refresh this conversation."
            } finally {
                isLoading = false
            }
        }
    }

    fun createChat() {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                createNewThread()
                drawerState.close()
            } catch (error: Exception) {
                errorMessage = error.message ?: "Could not create a new conversation."
            } finally {
                isLoading = false
            }
        }
    }

    fun chooseThread(thread: TotemThread) {
        scope.launch {
            isLoading = true
            errorMessage = null
            try {
                openThread(thread)
                drawerState.close()
            } catch (error: Exception) {
                errorMessage = error.message ?: "Could not open this conversation."
            } finally {
                isLoading = false
            }
        }
    }

    fun sendMessage() {
        val text = draft.trim()
        val activeWorkspace = workspace
        val activeThread = selectedThread
        if (text.isBlank() || isSending || activeWorkspace == null || activeThread == null) return

        draft = ""
        keyboard?.hide()
        focusManager.clearFocus()

        val userId = "local-user-${System.nanoTime()}"
        val assistantId = "local-assistant-${System.nanoTime()}"
        messages.add(ChatUiMessage(userId, true, text))
        messages.add(ChatUiMessage(assistantId, false, "Thinking…"))
        isSending = true

        scope.launch {
            try {
                api.sendMessage(
                    workspaceSlug = activeWorkspace.slug,
                    threadSlug = activeThread.slug,
                    message = text
                ) { delta ->
                    val index = messages.indexOfFirst { it.id == assistantId }
                    if (index >= 0) {
                        val current = messages[index]
                        val updatedText = if (current.text == "Thinking…") delta else current.text + delta
                        messages[index] = current.copy(text = updatedText)
                    }
                }
                refreshSelectedThread()
            } catch (error: Exception) {
                val index = messages.indexOfFirst { it.id == assistantId }
                if (index >= 0) {
                    messages[index] = messages[index].copy(
                        text = error.message ?: "TOTEM could not complete this response.",
                        isError = true
                    )
                }
            } finally {
                isSending = false
            }
        }
    }

    LaunchedEffect(messages.size, isSending) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    if (recoveryCodes != null) {
        val codes = recoveryCodes.orEmpty()
        AlertDialog(
            onDismissRequest = { recoveryCodes = null },
            containerColor = InkElevated,
            title = { Text("Save your recovery codes", color = TextPrimary) },
            text = {
                Text(
                    codes.joinToString("\n"),
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        clipboard.setText(AnnotatedString(codes.joinToString("\n")))
                        recoveryCodes = null
                    }
                ) { Text("COPY AND CLOSE", color = Violet) }
            },
            dismissButton = {
                TextButton(onClick = { recoveryCodes = null }) {
                    Text("CLOSE", color = TextSecondary)
                }
            }
        )
    }

    if (!signedIn) {
        SignInPanel(
            identifier = identifier,
            onIdentifierChange = { identifier = it },
            password = password,
            onPasswordChange = { password = it },
            showPassword = showPassword,
            onTogglePassword = { showPassword = !showPassword },
            loading = isSigningIn,
            error = errorMessage,
            onSignIn = { signIn() }
        )
        return
    }

    if (needsDeviceConnection) {
        DeviceConnectionPanel(
            loading = isConnecting,
            error = errorMessage,
            onConnect = { connectDevice() }
        )
        return
    }

    if (!isReady) {
        LoadingPanel(
            loading = isLoading,
            error = errorMessage,
            onRetry = {
                scope.launch {
                    isLoading = true
                    errorMessage = null
                    try {
                        api.ensureMobileDevice()
                        loadChatFromStart()
                    } catch (error: Exception) {
                        errorMessage = error.message ?: "Could not load TOTEM Chat."
                    } finally {
                        isLoading = false
                    }
                }
            }
        )
        return
    }

    val activeThread = selectedThread
    val activeWorkspace = workspace
    val selectedSlug = activeThread?.slug

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = InkElevated,
                drawerContentColor = TextPrimary
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("CHAT HISTORY", style = MaterialTheme.typography.titleMedium, color = TextPrimary)
                        IconButton(onClick = { scope.launch { drawerState.close() } }) {
                            Icon(Icons.Default.Menu, contentDescription = "Close history", tint = TextSecondary)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { createChat() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Violet,
                            contentColor = Color.White
                        )
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("NEW CHAT")
                    }
                    Spacer(Modifier.height(14.dp))
                    Divider(color = Outline)
                    Spacer(Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                        contentPadding = PaddingValues(bottom = 20.dp)
                    ) {
                        items(threads, key = { it.slug }) { thread ->
                            val title = historyTitle(thread, history)
                            NavigationDrawerItem(
                                label = {
                                    Text(
                                        text = title,
                                        maxLines = 2,
                                        color = if (thread.slug == selectedSlug) TextPrimary else TextSecondary
                                    )
                                },
                                selected = thread.slug == selectedSlug,
                                onClick = { chooseThread(thread) },
                                colors = NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = Violet.copy(alpha = 0.16f),
                                    selectedTextColor = TextPrimary,
                                    unselectedTextColor = TextSecondary
                                )
                            )
                        }
                    }
                    Text(
                        text = activeWorkspace?.name ?: "TOTEM",
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                }
            }
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Ink)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(InkElevated)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { scope.launch { drawerState.open() } }) {
                    Icon(Icons.Default.Menu, contentDescription = "Chat history", tint = Violet)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("TOTEM CHAT", color = TextPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(modelTag, color = TextSecondary, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = { refreshChat() }, enabled = !isSending && !isLoading) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh conversation", tint = TextSecondary)
                }
                IconButton(onClick = { createChat() }, enabled = !isSending && !isLoading) {
                    Icon(Icons.Default.Add, contentDescription = "New chat", tint = Violet)
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Text(
                    text = errorMessage.orEmpty(),
                    color = Danger,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Danger.copy(alpha = 0.08f))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            if (messages.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Start a new conversation", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Ask TOTEM anything. Your previous chats are in the menu.",
                            color = TextSecondary,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(messages, key = { it.id }) { message ->
                        ChatBubble(message)
                    }
                }
            }

            if (isSending) {
                Text(
                    "TOTEM is responding…",
                    color = TextSecondary,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(InkElevated)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Message TOTEM…", color = TextSecondary) },
                    maxLines = 4,
                    shape = RoundedCornerShape(18.dp),
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(onSend = { sendMessage() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Violet,
                        unfocusedBorderColor = Outline,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = Violet,
                        focusedContainerColor = Surface1,
                        unfocusedContainerColor = Surface1
                    )
                )
                Spacer(Modifier.width(8.dp))
                IconButton(
                    onClick = { sendMessage() },
                    enabled = draft.isNotBlank() && !isSending
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Send message",
                        tint = if (draft.isNotBlank() && !isSending) Violet else TextSecondary,
                        modifier = Modifier.size(25.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SignInPanel(
    identifier: String,
    onIdentifierChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    showPassword: Boolean,
    onTogglePassword: () -> Unit,
    loading: Boolean,
    error: String?,
    onSignIn: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Text("TOTEM", color = TextPrimary, style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(8.dp))
        Text("SIGN IN", color = Violet, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(20.dp))
        Text(
            "Sign in with your hosted TOTEM account to open your private workspace.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.height(20.dp))
        OutlinedTextField(
            value = identifier,
            onValueChange = onIdentifierChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Email or username") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Violet,
                unfocusedBorderColor = Outline,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Violet,
                focusedContainerColor = Surface1,
                unfocusedContainerColor = Surface1
            )
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = onTogglePassword) {
                    Text(if (showPassword) "HIDE" else "SHOW", color = Violet)
                }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onSignIn() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Violet,
                unfocusedBorderColor = Outline,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Violet,
                focusedContainerColor = Surface1,
                unfocusedContainerColor = Surface1
            )
        )
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = Danger, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onSignIn,
            enabled = !loading && password.isNotBlank(),
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Violet,
                contentColor = Color.White
            )
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("SIGN IN")
            }
        }
    }
}

@Composable
private fun DeviceConnectionPanel(
    loading: Boolean,
    error: String?,
    onConnect: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("CONNECT THIS DEVICE", color = TextPrimary, style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(10.dp))
        Text(
            "TOTEM uses an approved mobile-device token for native chat. This connects the app to the hosted backend without embedding a private API key.",
            color = TextSecondary,
            style = MaterialTheme.typography.bodyMedium
        )
        if (!error.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(error, color = Danger, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onConnect,
            enabled = !loading,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Violet,
                contentColor = Color.White
            )
        ) {
            if (loading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(20.dp),
                    color = Color.White,
                    strokeWidth = 2.dp
                )
            } else {
                Text("CONNECT")
            }
        }
    }
}

@Composable
private fun LoadingPanel(
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (loading) {
            CircularProgressIndicator(color = Violet)
            Spacer(Modifier.height(16.dp))
            Text("Connecting to TOTEM…", color = TextSecondary)
        } else {
            Text(
                error ?: "Could not open TOTEM Chat.",
                color = Danger,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Violet,
                    contentColor = Color.White
                )
            ) {
                Text("RETRY")
            }
        }
    }
}

@Composable
private fun ChatBubble(message: ChatUiMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.90f),
            color = when {
                message.isError -> Danger.copy(alpha = 0.12f)
                message.isUser -> Violet.copy(alpha = 0.18f)
                else -> Surface1
            },
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                when {
                    message.isError -> Danger.copy(alpha = 0.35f)
                    message.isUser -> Violet.copy(alpha = 0.30f)
                    else -> Outline
                }
            )
        ) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                Text(
                    if (message.isUser) "YOU" else "TOTEM",
                    color = if (message.isError) Danger else Violet,
                    style = MaterialTheme.typography.labelSmall
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = message.text,
                    color = if (message.isError) Danger else TextPrimary,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

private fun historyTitle(
    thread: TotemThread,
    content: TotemWorkspaceContent?
): String {
    val firstPrompt = content?.chats
        ?.filter { it.threadId == thread.id }
        ?.minByOrNull { it.id }
        ?.prompt
        ?.takeIf { it.isNotBlank() }
    return firstPrompt?.take(44) ?: thread.name.ifBlank { "Conversation" }
}
