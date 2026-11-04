package xyz.totemai.app.web

import android.net.Uri
import xyz.totemai.app.TotemConfig

object OriginPolicy {
    private val NON_HTTP = setOf("javascript", "data", "blob", "about", "file", "content", "intent")

    private val WALLET_SCHEMES = setOf(
        "wc", "wc2", "metamask", "trust", "rainbow", "coinbase", "uniswap",
        "phantom", "zerion", "imtoken", "bitkeep", "avalanche", "ledgerlive",
        "mathwallet", "bitpay", "exodus", "safepal", "okx", "bybit", "kraken"
    )

    private val TRUSTED_HOSTS = setOf(
        "beta.totemai.xyz",
        "totemai.xyz",
        "www.totemai.xyz"
    )

    fun isTrustedHost(url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            host in TRUSTED_HOSTS
    }

    fun isTrustedOrigin(origin: String): Boolean {
        val uri = runCatching { Uri.parse(origin.trimEnd('/')) }.getOrNull() ?: return false
        val host = uri.host?.lowercase() ?: return false
        val expectedHost = Uri.parse(TotemConfig.ORIGIN).host?.lowercase() ?: return false
        val path = uri.path.orEmpty()
        return uri.scheme.equals("https", ignoreCase = true) &&
            uri.userInfo == null &&
            (uri.port == -1 || uri.port == 443) &&
            host == expectedHost &&
            (path.isEmpty() || path == "/") &&
            uri.query == null && uri.fragment == null
    }

    fun isNonHttp(scheme: String?): Boolean =
        scheme?.lowercase()?.let { it in NON_HTTP } ?: false

    fun isWallet(scheme: String?): Boolean =
        scheme?.lowercase()?.let { it in WALLET_SCHEMES } ?: false
}
