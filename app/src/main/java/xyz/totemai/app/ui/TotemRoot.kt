package xyz.totemai.app.ui

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import kotlinx.coroutines.launch
import xyz.totemai.app.DeepTarget
import xyz.totemai.app.NavEvent
import xyz.totemai.app.NavEvents
import xyz.totemai.app.NetworkMonitor
import xyz.totemai.app.Routes
import xyz.totemai.app.SessionStore
import xyz.totemai.app.data.ApiException
import xyz.totemai.app.data.ApiMessage
import xyz.totemai.app.data.ApiWorkspace
import xyz.totemai.app.data.ApiThread
import xyz.totemai.app.data.CreditsSnapshot
import xyz.totemai.app.data.TotemApi
import xyz.totemai.app.data.WalletSnapshot
import xyz.totemai.app.route
import xyz.totemai.app.ui.screens.HomeScreen
import xyz.totemai.app.ui.screens.ProfileScreen
import xyz.totemai.app.ui.screens.SplashScreen
import xyz.totemai.app.ui.theme.Ink

@Composable
fun TotemRoot(
    activity: ComponentActivity,
    events: NavEvents,
    store: SessionStore,
    monitor: NetworkMonitor,
    startTarget: DeepTarget
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val route = backStackEntry?.destination?.route ?: startTarget.route()
    val scope = rememberCoroutineScope()

    var showSplash by remember { mutableStateOf(true) }
    var sessionTick by remember { mutableIntStateOf(0) }
    var loginIdentifier by remember { mutableStateOf("") }
    var loginPassword by remember { mutableStateOf("") }
    var loginLoading by remember { mutableStateOf(false) }
    var loginError by remember { mutableStateOf<String?>(null) }

    val online by monitor.online.collectAsState()
    val signedIn = remember(sessionTick) { store.signedIn() }
    val displayName = remember(sessionTick) { readName(store.user()) }

    val workspaces = remember { mutableStateListOf<ApiWorkspace>() }
    val messages = remember { mutableStateListOf<ApiMessage>() }
    var selectedWorkspace by remember { mutableStateOf<ApiWorkspace?>(null) }
    var activeThread by remember { mutableStateOf<ApiThread?>(null) }
    var workspaceLoading by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    var workspaceError by remember { mutableStateOf<String?>(null) }

    var credits by remember { mutableStateOf<CreditsSnapshot?>(null) }
    var wallet by remember { mutableStateOf<WalletSnapshot?>(null) }
    var walletLoading by remember { mutableStateOf(false) }
    var walletError by remember { mutableStateOf<String?>(null) }

    fun loadWorkspaceData() {
        val token = store.token() ?: return
        scope.launch {
            workspaceLoading = true
            workspaceError = null
            try {
                val found = TotemApi.getWorkspaces(token)
                workspaces.clear()
                workspaces.addAll(found)
                val current = selectedWorkspace?.let { old -> found.firstOrNull { it.slug == old.slug } }
                selectedWorkspace = current ?: found.firstOrNull()
                if (selectedWorkspace != null) {
                    val threads = TotemApi.getThreads(token, selectedWorkspace!!.slug)
                    activeThread = threads.firstOrNull()
                    val history = TotemApi.getChatHistory(token, selectedWorkspace!!.slug, activeThread?.slug)
                    messages.clear()
                    messages.addAll(history)
                } else {
                    messages.clear()
                }
            } catch (e: Exception) {
                workspaceError = e.message ?: "Could not load workspaces."
            } finally {
                workspaceLoading = false
            }
        }
    }

    fun createNewChat() {
        val token = store.token() ?: return
        val workspace = selectedWorkspace ?: return
        scope.launch {
            workspaceError = null
            try {
                val thread = TotemApi.createThread(token, workspace.slug)
                activeThread = thread
                messages.clear()
            } catch (e: Exception) {
                workspaceError = e.message ?: "Could not create a new chat."
            }
        }
    }

    fun loadWalletData() {
        val token = store.token() ?: return
        scope.launch {
            walletLoading = true
            walletError = null
            try {
                credits = TotemApi.getCredits(token)
                wallet = TotemApi.getWalletBalance(token)
            } catch (e: Exception) {
                walletError = e.message ?: "Could not load account balances."
            } finally {
                walletLoading = false
            }
        }
    }

    LaunchedEffect(sessionTick, route) {
        if (signedIn && route == Routes.WORKSPACE) loadWorkspaceData()
        if (signedIn && route == Routes.WALLET) loadWalletData()
    }

    LaunchedEffect(Unit) {
        events.events.collect { event ->
            when (event) {
                is NavEvent.Go -> navController.navigateToTab(event.target.route())
            }
        }
    }

    BackHandler {
        if (!navController.popBackStack()) activity.finish()
    }

    Box(Modifier.fillMaxSize().background(Ink)) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                TotemBottomBar(
                    current = route,
                    onSelect = { target -> navController.navigateToTab(target) }
                )
            }
        ) { inner ->
            Box(Modifier.fillMaxSize().padding(inner)) {
                NavHost(
                    navController = navController,
                    startDestination = startTarget.route(),
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            online = online,
                            signedIn = signedIn,
                            displayName = displayName,
                            onOpenWorkspace = { navController.navigateToTab(Routes.WORKSPACE) },
                            onOpenWallet = { navController.navigateToTab(Routes.WALLET) },
                            onOpenProfile = { navController.navigateToTab(Routes.PROFILE) }
                        )
                    }
                    composable(Routes.WORKSPACE) {
                        if (!signedIn) {
                            LoginScreen(
                                identifier = loginIdentifier,
                                password = loginPassword,
                                loading = loginLoading,
                                error = loginError,
                                onIdentifierChange = { loginIdentifier = it; loginError = null },
                                onPasswordChange = { loginPassword = it; loginError = null },
                                onLogin = {
                                    scope.launch {
                                        loginLoading = true
                                        loginError = null
                                        try {
                                            val result = TotemApi.login(loginIdentifier, loginPassword)
                                            store.save(result.token, result.userJson)
                                            loginPassword = ""
                                            sessionTick++
                                        } catch (e: Exception) {
                                            loginError = e.message ?: "Could not sign in."
                                        } finally {
                                            loginLoading = false
                                        }
                                    }
                                }
                            )
                        } else {
                            WorkspaceScreen(
                                workspaces = workspaces,
                                selectedWorkspace = selectedWorkspace,
                                messages = messages,
                                draft = draft,
                                loading = workspaceLoading,
                                sending = sending,
                                error = workspaceError,
                                onSelectWorkspace = { workspace ->
                                    selectedWorkspace = workspace
                                    activeThread = null
                                    messages.clear()
                                    loadWorkspaceData()
                                },
                                onRefresh = { loadWorkspaceData() },
                                onDraftChange = { draft = it },
                                onSend = {
                                    val token = store.token()
                                    val workspace = selectedWorkspace
                                    val prompt = draft.trim()
                                    if (token != null && workspace != null && prompt.isNotEmpty() && !sending) {
                                        draft = ""
                                        workspaceError = null
                                        messages.add(ApiMessage("local-user-" + System.currentTimeMillis(), "user", prompt))
                                        val assistantId = "local-assistant-" + System.currentTimeMillis()
                                        messages.add(ApiMessage(assistantId, "assistant", ""))
                                        sending = true
                                        scope.launch {
                                            try {
                                                TotemApi.streamChat(token, workspace.slug, prompt, activeThread?.slug) { text ->
                                                    val lastIndex = messages.lastIndex
                                                    if (lastIndex >= 0 && messages[lastIndex].id == assistantId) {
                                                        messages[lastIndex] = messages[lastIndex].copy(content = text)
                                                    }
                                                }
                                                loadWalletData()
                                            } catch (e: Exception) {
                                                workspaceError = e.message ?: "The chat request failed."
                                            } finally {
                                                sending = false
                                            }
                                        }
                                    }
                                },
                                onNewChat = { createNewChat() }
                            )
                        }
                    }
                    composable(Routes.WALLET) {
                        if (!signedIn) {
                            LoginScreen(
                                identifier = loginIdentifier,
                                password = loginPassword,
                                loading = loginLoading,
                                error = loginError,
                                onIdentifierChange = { loginIdentifier = it; loginError = null },
                                onPasswordChange = { loginPassword = it; loginError = null },
                                onLogin = {
                                    scope.launch {
                                        loginLoading = true
                                        loginError = null
                                        try {
                                            val result = TotemApi.login(loginIdentifier, loginPassword)
                                            store.save(result.token, result.userJson)
                                            loginPassword = ""
                                            sessionTick++
                                        } catch (e: Exception) {
                                            loginError = e.message ?: "Could not sign in."
                                        } finally {
                                            loginLoading = false
                                        }
                                    }
                                }
                            )
                        } else {
                            WalletScreen(
                                credits = credits,
                                wallet = wallet,
                                loading = walletLoading,
                                error = walletError,
                                onRefresh = { loadWalletData() },
                                onOpenWalletConnection = {
                                    walletError = "Wallet linking is not yet exposed as a native API in the inspected backend. No website screen has been embedded."
                                }
                            )
                        }
                    }
                    composable(Routes.PROFILE) {
                        ProfileScreen(
                            store = store,
                            signedIn = signedIn,
                            displayName = displayName,
                            onOpenWorkspace = { navController.navigateToTab(Routes.WORKSPACE) },
                            onOpenWallet = { navController.navigateToTab(Routes.WALLET) },
                            onSignOut = {
                                store.clear()
                                workspaces.clear()
                                messages.clear()
                                selectedWorkspace = null
                                activeThread = null
                                credits = null
                                wallet = null
                                loginIdentifier = ""
                                loginPassword = ""
                                sessionTick++
                                navController.navigateToTab(Routes.HOME)
                            }
                        )
                    }
                }
            }
        }

        AnimatedVisibility(visible = showSplash, exit = fadeOut(tween(360))) {
            SplashScreen(onFinished = { showSplash = false })
        }
    }
}

private fun NavHostController.navigateToTab(target: String) {
    if (currentDestination?.route == target) return
    navigate(target, navOptions {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.startDestinationId) { saveState = true }
    })
}

private fun readName(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val obj = org.json.JSONObject(raw)
        obj.optString("username").takeIf { it.isNotBlank() }
            ?: obj.optString("displayName").takeIf { it.isNotBlank() }
    }.getOrNull()
}
