package xyz.totemai.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri

object Intents {
    private val allowedExternalSchemes = setOf(
        "http", "https", "wc", "wc2", "metamask", "trust", "rainbow", "coinbase",
        "uniswap", "phantom", "zerion", "imtoken", "bitkeep", "avalanche",
        "ledgerlive", "mathwallet", "bitpay", "exodus", "safepal", "okx", "bybit", "kraken"
    )

    fun open(context: Context, uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        return when (scheme) {
            "intent" -> openIntentUri(context, uri.toString())
            in allowedExternalSchemes -> openExternal(context, uri)
            else -> false
        }
    }

    fun openUrl(context: Context, url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() != "https") return false
        return openExternal(context, uri)
    }

    fun openExternal(context: Context, uri: Uri): Boolean {
        val scheme = uri.scheme?.lowercase() ?: return false
        if (scheme !in allowedExternalSchemes) return false
        val intent = Intent(Intent.ACTION_VIEW, uri)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try {
            context.startActivity(intent)
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    private fun openIntentUri(context: Context, url: String): Boolean {
        val parsed = runCatching {
            Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
        }.getOrNull() ?: return false

        val data = parsed.data
        val scheme = data?.scheme?.lowercase()
        if (scheme == null || scheme !in allowedExternalSchemes) {
            val fallback = parsed.getStringExtra("browser_fallback_url")
            return openHttpsFallback(context, fallback)
        }

        // Rebuild as ACTION_VIEW; do not honor embedded actions, components or flags.
        val safeIntent = Intent(Intent.ACTION_VIEW, data)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        parsed.`package`?.takeIf { it.isNotBlank() }?.let(safeIntent::setPackage)

        val started = runCatching {
            context.startActivity(safeIntent)
            true
        }.getOrDefault(false)
        if (started) return true

        val fallback = parsed.getStringExtra("browser_fallback_url")
        return openHttpsFallback(context, fallback)
    }

    private fun openHttpsFallback(context: Context, fallback: String?): Boolean {
        if (fallback.isNullOrBlank()) return false
        val uri = runCatching { Uri.parse(fallback) }.getOrNull() ?: return false
        if (uri.scheme?.lowercase() != "https") return false
        return openExternal(context, uri)
    }
}
