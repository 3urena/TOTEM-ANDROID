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
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navOptions
import org.json.JSONObject
import xyz.totemai.app.DeepTarget
import xyz.totemai.app.NavEvent
import xyz.totemai.app.NavEvents
import xyz.totemai.app.NetworkMonitor
import xyz.totemai.app.Routes
import xyz.totemai.app.SessionStore
import xyz.totemai.app.route
import xyz.totemai.app.ui.screens.ChatScreen
import xyz.totemai.app.ui.screens.HomeScreen
import xyz.totemai.app.ui.screens.NativeSignInScreen
import xyz.totemai.app.ui.screens.ProfileScreen
import xyz.totemai.app.ui.screens.SplashScreen
import xyz.totemai.app.ui.screens.WalletScreen
import xyz.totemai.app.ui.theme.Ink
import java.util.UUID

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
    val appLaunchId = remember { UUID.randomUUID().toString() }
    var showSplash by remember { androidx.compose.runtime.mutableStateOf(true) }
    var sessionTick by remember { mutableIntStateOf(0) }
    var creditRefreshTick by remember { mutableIntStateOf(0) }

    val online by monitor.online.collectAsState()
    val signedIn = remember(sessionTick) { store.signedIn() }
    val displayName = remember(sessionTick) { readName(store.user()) }

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

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink)
    ) {
        Scaffold(
            containerColor = Ink,
            bottomBar = {
                TotemBottomBar(
                    current = route,
                    onSelect = { target -> navController.navigateToTab(target) }
                )
            }
        ) { inner ->
            Box(
                Modifier
                    .fillMaxSize()
                    .padding(inner)
            ) {
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
                    composable(Routes.PROFILE) {
                        ProfileScreen(
                            store = store,
                            signedIn = signedIn,
                            displayName = displayName,
                            onOpenWorkspace = { navController.navigateToTab(Routes.WORKSPACE) },
                            onOpenWallet = { navController.navigateToTab(Routes.WALLET) },
                            onSignOut = {
                                store.clear()
                                sessionTick++
                                navController.navigateToTab(Routes.HOME)
                            }
                        )
                    }
                    composable(Routes.WORKSPACE) {
                        if (signedIn) {
                            ChatScreen(
                                store = store,
                                signedIn = true,
                                launchId = appLaunchId,
                                onSignIn = {},
                                onCreditsChanged = { creditRefreshTick++ }
                            )
                        } else {
                            NativeSignInScreen(
                                store = store,
                                onSignedIn = { sessionTick++ }
                            )
                        }
                    }
                    composable(Routes.WALLET) {
                        WalletScreen(
                            store = store,
                            signedIn = signedIn,
                            onSignIn = { navController.navigateToTab(Routes.WORKSPACE) },
                            refreshTrigger = creditRefreshTick
                        )
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showSplash,
            exit = fadeOut(tween(360))
        ) {
            SplashScreen(onFinished = { showSplash = false })
        }
    }
}

private fun NavHostController.navigateToTab(target: String) {
    if (currentDestination?.route == target) return
    navigate(target, navOptions {
        launchSingleTop = true
        restoreState = true
        popUpTo(graph.startDestinationId) {
            saveState = true
        }
    })
}

private fun readName(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return runCatching {
        val obj = JSONObject(raw)
        obj.optString("username").takeIf { it.isNotBlank() }
            ?: obj.optString("displayName").takeIf { it.isNotBlank() }
    }.getOrNull()
}
