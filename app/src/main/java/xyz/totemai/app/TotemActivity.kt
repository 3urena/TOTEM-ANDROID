package xyz.totemai.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.mutableStateOf
import androidx.core.view.WindowCompat
import android.content.res.Configuration
import xyz.totemai.app.ui.TotemRoot
import xyz.totemai.app.ui.theme.TotemTheme

class TotemActivity : ComponentActivity() {
    private lateinit var events: NavEvents
    private lateinit var store: SessionStore
    private lateinit var monitor: NetworkMonitor
    private var initialTarget: DeepTarget = DeepTarget.NONE
    private val themeModeState = mutableStateOf("dark")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        store = TotemApp.instance.sessionStore
        monitor = TotemApp.instance.networkMonitor
        themeModeState.value = store.themeMode()
        applySystemBars(themeModeState.value)
        events = NavEvents()
        readIntent(intent, first = true)

        setContent {
            TotemTheme(themeMode = themeModeState.value) {
                TotemRoot(
                    activity = this,
                    events = events,
                    store = store,
                    monitor = monitor,
                    startTarget = initialTarget,
                    themeMode = themeModeState.value,
                    onThemeModeChange = { mode ->
                        store.saveThemeMode(mode)
                        themeModeState.value = mode
                        applySystemBars(mode)
                    }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent, first = false)
    }

    private fun applySystemBars(mode: String) {
        val lightBars = when (mode) {
            "light" -> true
            "dark" -> false
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_NO
        }
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        controller.isAppearanceLightStatusBars = lightBars
        controller.isAppearanceLightNavigationBars = lightBars
    }

    private fun readIntent(intent: Intent?, first: Boolean) {
        val uri = intent?.data ?: return
        if (uri.scheme != TotemConfig.DEEP_LINK_SCHEME) return
        val target = when (uri.host?.lowercase()) {
            "workspace", "chat" -> DeepTarget.WORKSPACE
            "wallet" -> DeepTarget.WALLET
            "profile" -> DeepTarget.PROFILE
            "home" -> DeepTarget.HOME
            else -> DeepTarget.NONE
        }
        if (target == DeepTarget.NONE) return
        if (first) initialTarget = target else events.send(NavEvent.Go(target))
    }
}
