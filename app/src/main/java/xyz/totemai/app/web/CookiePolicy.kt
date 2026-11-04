package xyz.totemai.app.web

import android.webkit.CookieManager
import android.webkit.WebView

object CookiePolicy {
    fun configure(view: WebView) {
        val manager = CookieManager.getInstance()
        manager.setAcceptCookie(true)
        // Wallet and account flows should use first-party cookies unless the host proves otherwise.
        manager.setAcceptThirdPartyCookies(view, false)
    }

    fun flush() {
        runCatching { CookieManager.getInstance().flush() }
    }
}
