package xyz.totemai.app.web

import xyz.totemai.app.TotemConfig

object UserAgent {
    fun build(original: String?): String {
        val base = (original ?: "")
            .replace("; wv", "")
            .replace("Version/4.0 ", "")
            .trim()
        return base + TotemConfig.UA_SUFFIX
    }
}
