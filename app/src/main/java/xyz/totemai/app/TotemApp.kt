package xyz.totemai.app

import android.app.Application

class TotemApp : Application() {
    lateinit var sessionStore: SessionStore
        private set

    lateinit var networkMonitor: NetworkMonitor
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
        sessionStore = SessionStore.get(this)
        networkMonitor = NetworkMonitor(this)
        networkMonitor.start()
    }

    companion object {
        lateinit var instance: TotemApp
            private set
    }
}
