package xyz.totemai.app

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

enum class DeepTarget { NONE, HOME, WORKSPACE, WALLET, PROFILE }

sealed interface NavEvent {
    data class Go(val target: DeepTarget) : NavEvent
}

class NavEvents {
    private val channel = Channel<NavEvent>(Channel.BUFFERED)
    val events: Flow<NavEvent> = channel.receiveAsFlow()

    fun send(event: NavEvent) {
        channel.trySend(event)
    }
}

object Routes {
    const val HOME = "home"
    const val WORKSPACE = "workspace"
    const val WALLET = "wallet"
    const val PROFILE = "profile"
}

fun DeepTarget.route(): String = when (this) {
    DeepTarget.WORKSPACE -> Routes.WORKSPACE
    DeepTarget.WALLET -> Routes.WALLET
    DeepTarget.PROFILE -> Routes.PROFILE
    else -> Routes.WORKSPACE
}
