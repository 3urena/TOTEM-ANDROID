package xyz.totemai.app.web

import android.os.Bundle
import android.webkit.WebView
import org.json.JSONObject
import xyz.totemai.app.SessionStore
import xyz.totemai.app.TotemConfig

class WebController(
    private val store: SessionStore,
    savedInstanceState: Bundle?
) {
    var webView: WebView? = null
        private set

    var currentUrl: String = savedInstanceState
        ?.getString(KEY_CURRENT_URL)
        ?.takeIf { OriginPolicy.isTrustedHost(it) }
        ?: TotemConfig.HOME_URL
        private set

    private var restoredState: Bundle? = savedInstanceState?.getBundle(KEY_STATE)
    private var pendingUrl: String? = null

    fun attach(view: WebView) {
        webView = view
        view.url?.takeIf { OriginPolicy.isTrustedHost(it) }?.let { currentUrl = it }
    }

    fun detach() {
        webView = null
    }

    fun consumeRestoredState(): Bundle? {
        val state = restoredState
        restoredState = null
        return state
    }

    fun takePending(): String? {
        val url = pendingUrl
        pendingUrl = null
        return url
    }

    fun onUrl(url: String?) {
        if (url != null && OriginPolicy.isTrustedHost(url)) currentUrl = url
    }

    fun navigateTo(url: String) {
        if (!OriginPolicy.isTrustedHost(url)) return
        currentUrl = url
        val view = webView
        if (view == null) {
            pendingUrl = url
            return
        }
        if (view.url == url) return
        view.loadUrl(url)
    }

    fun canGoBack(): Boolean = webView?.canGoBack() == true

    fun goBack() {
        webView?.takeIf { it.canGoBack() }?.goBack()
    }

    fun reload() {
        webView?.reload() ?: navigateTo(currentUrl)
    }

    fun saveState(outState: Bundle) {
        outState.putString(KEY_CURRENT_URL, currentUrl)
        val view = webView ?: return
        runCatching {
            val inner = Bundle()
            view.saveState(inner)
            outState.putBundle(KEY_STATE, inner)
        }
    }

    fun signOut() {
        val view = webView
        store.clear()
        if (view == null) {
            navigateTo(TotemConfig.LOGIN_URL)
            return
        }
        val tokenKey = JSONObject.quote(TotemConfig.KEY_TOKEN)
        val userKey = JSONObject.quote(TotemConfig.KEY_USER)
        val js = """
            (function() {
              try {
                localStorage.removeItem($tokenKey);
                localStorage.removeItem($userKey);
              } catch (e) {}
            })();
        """.trimIndent()
        view.evaluateJavascript(js) {
            navigateTo(TotemConfig.LOGIN_URL)
        }
    }

    companion object {
        private const val KEY_STATE = "totem.web.state"
        private const val KEY_CURRENT_URL = "totem.web.current_url"
    }
}
