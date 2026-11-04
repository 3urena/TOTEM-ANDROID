package xyz.totemai.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import xyz.totemai.app.ui.TotemRoot
import xyz.totemai.app.ui.theme.TotemTheme
import xyz.totemai.app.web.WebController

class TotemActivity : ComponentActivity() {
    private lateinit var controller: WebController
    private lateinit var events: NavEvents
    private lateinit var store: SessionStore
    private lateinit var monitor: NetworkMonitor
    private var initialTarget: DeepTarget = DeepTarget.NONE

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        store = TotemApp.instance.sessionStore
        monitor = TotemApp.instance.networkMonitor
        events = NavEvents()
        controller = WebController(store, savedInstanceState)
        readIntent(intent, first = true)

        setContent {
            TotemTheme {
                TotemRoot(
                    activity = this,
                    controller = controller,
                    events = events,
                    store = store,
                    monitor = monitor,
                    startTarget = initialTarget
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readIntent(intent, first = false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        controller.saveState(outState)
        super.onSaveInstanceState(outState)
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
