package xyz.totemai.app.web

import android.net.Uri
import android.webkit.WebView
import androidx.webkit.JavaScriptReplyProxy
import androidx.webkit.WebMessageCompat
import androidx.webkit.WebViewCompat
import org.json.JSONObject
import xyz.totemai.app.SessionStore
import xyz.totemai.app.TotemConfig

/** The bridge accepts messages only from the exact hosted origin and the main frame. */
class SessionBridge(
    private val store: SessionStore,
    private val onSessionChanged: () -> Unit
) : WebViewCompat.WebMessageListener {

    override fun onPostMessage(
        view: WebView,
        message: WebMessageCompat,
        sourceOrigin: Uri,
        isMainFrame: Boolean,
        replyProxy: JavaScriptReplyProxy
    ) {
        if (!isMainFrame || !OriginPolicy.isTrustedOrigin(sourceOrigin.toString())) return
        val raw = message.data ?: return
        if (raw.length > MAX_MESSAGE_LENGTH) return
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return

        when (json.optString("type")) {
            "save" -> {
                val token = json.optString("token").takeIf {
                    it.isNotBlank() && it.length <= MAX_TOKEN_LENGTH
                } ?: return
                val user = json.optString("user").takeIf { it.isNotBlank() }
                store.save(token, user)
                onSessionChanged()
                replyProxy.postMessage(OK)
            }
            "clear" -> {
                store.clear()
                onSessionChanged()
                replyProxy.postMessage(OK)
            }
        }
    }

    companion object {
        private const val MAX_MESSAGE_LENGTH = 64 * 1024
        private const val MAX_TOKEN_LENGTH = 16 * 1024
        private const val OK = """{"ok":true}"""

        fun restoreJs(token: String, user: String?): String {
            val tokenLiteral = JSONObject.quote(token)
            val userLiteral = JSONObject.quote(user ?: "")
            val tokenKey = JSONObject.quote(TotemConfig.KEY_TOKEN)
            val userKey = JSONObject.quote(TotemConfig.KEY_USER)
            return """
                (function() {
                  try {
                    localStorage.setItem($tokenKey, $tokenLiteral);
                    if ($userLiteral) {
                      localStorage.setItem($userKey, $userLiteral);
                    } else {
                      localStorage.removeItem($userKey);
                    }
                  } catch (e) {}
                })();
            """.trimIndent()
        }

        val CAPTURE_JS: String = run {
            val tokenKey = JSONObject.quote(TotemConfig.KEY_TOKEN)
            val userKey = JSONObject.quote(TotemConfig.KEY_USER)
            val bridgeName = TotemConfig.BRIDGE_NAME
            """
                (function() {
                  try {
                    var token = localStorage.getItem($tokenKey);
                    var user = localStorage.getItem($userKey) || '';
                    if (window.$bridgeName) {
                      if (token) {
                        window.$bridgeName.postMessage(
                          JSON.stringify({type: 'save', token: token, user: user})
                        );
                      } else {
                        window.$bridgeName.postMessage(JSON.stringify({type: 'clear'}));
                      }
                    }
                  } catch (e) {}
                })();
            """.trimIndent()
        }
    }
}
