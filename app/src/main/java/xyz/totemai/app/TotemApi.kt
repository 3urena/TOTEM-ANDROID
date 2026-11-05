package xyz.totemai.app

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class TotemWorkspace(
    val slug: String,
    val name: String
)

data class TotemChatThread(
    val id: Long,
    val slug: String,
    val title: String,
    val preview: String
)

data class TotemChatMessage(
    val id: String,
    val fromUser: Boolean,
    val text: String
)

data class TotemWorkspaceSnapshot(
    val threads: List<TotemChatThread>,
    val messagesByThread: Map<String, List<TotemChatMessage>>
)

data class TotemWalletBalance(
    val available: Boolean,
    val noWallet: Boolean,
    val amount: String?,
    val symbol: String?,
    val chainId: Int?,
    val error: String?
)

data class TotemLoginResult(
    val valid: Boolean,
    val token: String?,
    val userJson: String?,
    val recoveryCodes: List<String>,
    val message: String?
)

sealed interface TotemChatStreamEvent {
    data class Text(val value: String) : TotemChatStreamEvent
    data class Failed(val message: String) : TotemChatStreamEvent
    data object Finished : TotemChatStreamEvent
}

/**
 * Thin Android client for the official TOTEM hosted API.
 *
 * All account-scoped operations, including chat streaming, use the official
 * signed-in account session token and the hosted platform's normal user APIs.
 */
class TotemApi(private val store: SessionStore) {
    data class AuthConfig(val multiUser: Boolean, val requiresAuth: Boolean)

    private val apiBase = TotemConfig.ORIGIN + "/api"

    /** Reads only the public authentication flags required to choose the login form. */
    suspend fun authConfig(): AuthConfig {
        val response = request("GET", "/setup-complete")
        val settings = response.optJSONObject("results")
            ?: throw IllegalStateException("The TOTEM server did not return its sign-in settings.")
        return AuthConfig(
            multiUser = settings.optBoolean("MultiUserMode", false),
            requiresAuth = settings.optBoolean("RequiresAuth", false)
        )
    }

    /** Uses the same official session endpoint as the hosted TOTEM login page. */
    suspend fun signIn(
        username: String,
        password: String,
        multiUser: Boolean
    ): TotemLoginResult {
        val body = JSONObject().put("password", password)
        if (multiUser) body.put("username", username)
        val response = request("POST", "/request-token", body = body)
        val valid = response.optBoolean("valid", false)
        val token = response.optString("token").takeIf { it.isNotBlank() && it != "null" }
        val user = response.optJSONObject("user")
        val codes = response.optJSONArray("recoveryCodes")
        val recoveryCodes = buildList {
            if (codes != null) {
                for (index in 0 until codes.length()) {
                    val code = codes.optString(index)
                    if (code.isNotBlank()) add(code)
                }
            }
        }
        return TotemLoginResult(
            valid = valid && !token.isNullOrBlank(),
            token = token,
            userJson = user?.toString(),
            recoveryCodes = recoveryCodes,
            message = response.optString("message").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    /** Public signup is exposed only when the server admin enables it. */
    suspend fun publicRegistrationEnabled(): Boolean {
        val response = request("GET", "/system/public-registration")
        return response.optBoolean("publicRegistration", false)
    }

    /** Creates a new account through the same public endpoint as the hosted login page. */
    suspend fun registerUser(username: String, email: String, password: String) {
        val response = request(
            method = "POST",
            path = "/system/new-user",
            body = JSONObject()
                .put("username", username)
                .put("email", email)
                .put("password", password)
        )
        if (!response.optBoolean("success", false)) {
            throw IllegalStateException(
                response.optString("error").takeIf { it.isNotBlank() }
                    ?: "Account registration failed."
            )
        }
    }

    /** Exchanges two saved recovery codes for a short-lived password-reset token. */
    suspend fun recoverAccount(username: String, recoveryCodes: List<String>): String {
        val codes = JSONArray()
        recoveryCodes.forEach { codes.put(it) }
        val response = request(
            method = "POST",
            path = "/system/recover-account",
            body = JSONObject()
                .put("username", username)
                .put("recoveryCodes", codes)
        )
        val token = response.optString("resetToken")
        if (!response.optBoolean("success", false) || token.isBlank()) {
            throw IllegalStateException(
                response.optString("message").takeIf { it.isNotBlank() }
                    ?: "Account recovery failed."
            )
        }
        return token
    }

    /** Completes password reset using the single-use token from account recovery. */
    suspend fun resetPassword(token: String, newPassword: String, confirmPassword: String) {
        val response = request(
            method = "POST",
            path = "/system/reset-password",
            body = JSONObject()
                .put("token", token)
                .put("newPassword", newPassword)
                .put("confirmPassword", confirmPassword)
        )
        if (!response.optBoolean("success", false)) {
            throw IllegalStateException(
                response.optString("error").takeIf { it.isNotBlank() }
                    ?: response.optString("message").takeIf { it.isNotBlank() }
                    ?: "Password reset failed."
            )
        }
    }

    /**
     * Uses the authenticated account session directly. The normal workspace
     * API supports every signed-in user; mobile-device registration is admin
     * restricted on multi-user deployments and must not be a prerequisite.
     */
    suspend fun workspaces(): List<TotemWorkspace> {
        val session = requireSession()
        val response = request("GET", "/workspaces", bearer = session)
        val array = response.optJSONArray("workspaces") ?: JSONArray()
        return buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val slug = item.optString("slug")
                if (slug.isBlank()) continue
                add(TotemWorkspace(slug, item.optString("name", "Workspace")))
            }
        }
    }

    suspend fun workspaceContent(workspaceSlug: String): TotemWorkspaceSnapshot {
        val session = requireSession()

        // The hosted client stores default-thread history separately from
        // each named thread. These are the same account-scoped endpoints the
        // official TOTEM web client uses.
        val rawThreads = request(
            "GET",
            "/workspace/$workspaceSlug/threads",
            bearer = session
        ).optJSONArray("threads") ?: JSONArray()

        val defaultHistory = request(
            "GET",
            "/workspace/$workspaceSlug/chats",
            bearer = session
        ).optJSONArray("history")
        val messages = LinkedHashMap<String, List<TotemChatMessage>>()
        messages[DEFAULT_THREAD] = parseHistory(defaultHistory, DEFAULT_THREAD)

        val threads = ArrayList<TotemChatThread>()
        val defaultPreview = messages[DEFAULT_THREAD]
            ?.firstOrNull { it.fromUser }?.text.orEmpty()
        threads.add(
            TotemChatThread(
                id = 0L,
                slug = DEFAULT_THREAD,
                title = if (defaultPreview.isNotBlank()) defaultPreview.take(44) else "Default Thread",
                preview = defaultPreview
            )
        )

        for (index in 0 until rawThreads.length()) {
            val item = rawThreads.optJSONObject(index) ?: continue
            val slug = item.optString("slug")
            if (slug.isBlank()) continue

            val history = request(
                "GET",
                "/workspace/$workspaceSlug/thread/$slug/chats",
                bearer = session
            ).optJSONArray("history")
            val threadMessages = parseHistory(history, slug)
            messages[slug] = threadMessages
            val preview = threadMessages.firstOrNull { it.fromUser }?.text.orEmpty()
            threads.add(
                TotemChatThread(
                    id = item.optLong("id", 0L),
                    slug = slug,
                    title = if (preview.isNotBlank()) preview.take(44)
                        else item.optString("name", "New chat"),
                    preview = preview
                )
            )
        }

        return TotemWorkspaceSnapshot(
            threads = threads.sortedByDescending { it.id },
            messagesByThread = messages
        )
    }

    suspend fun newThread(workspaceSlug: String): TotemChatThread {
        val session = requireSession()
        val response = request(
            method = "POST",
            path = "/workspace/$workspaceSlug/thread/new",
            bearer = session
        )
        val thread = response.optJSONObject("thread")
            ?: throw IllegalStateException("The TOTEM server did not return the new chat.")
        val slug = thread.optString("slug")
        if (slug.isBlank()) throw IllegalStateException("The new chat has no ID.")
        return TotemChatThread(
            id = thread.optLong("id", 0L),
            slug = slug,
            title = thread.optString("name", "New chat"),
            preview = ""
        )
    }

    fun streamChat(
        workspaceSlug: String,
        threadSlug: String,
        message: String
    ): Flow<TotemChatStreamEvent> = channelFlow {
        val session = requireSession()
        withContext(Dispatchers.IO) {
            val chatPath = if (threadSlug.isBlank() || threadSlug == DEFAULT_THREAD) {
                "/workspace/$workspaceSlug/stream-chat"
            } else {
                "/workspace/$workspaceSlug/thread/$threadSlug/stream-chat"
            }
            val body = JSONObject()
                .put("message", message)
                .put("attachments", JSONArray())

            val connection = (URL(apiBase + chatPath)
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 180_000
                doOutput = true
                setRequestProperty("Accept", "text/event-stream")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty("Authorization", "Bearer $session")
            }

            try {
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }
                val status = connection.responseCode
                if (status !in 200..299) {
                    val errorBody = readStream(connection.errorStream)
                    throw IllegalStateException(extractError(errorBody, status))
                }

                val reader = connection.inputStream.bufferedReader(Charsets.UTF_8)
                var data = StringBuilder()
                var finished = false
                while (true) {
                    val line = reader.readLine() ?: break
                    when {
                        line.startsWith("data:") -> {
                            if (data.isNotEmpty()) data.append('\n')
                            data.append(line.removePrefix("data:").trimStart())
                        }
                        line.isBlank() -> {
                            if (data.isNotEmpty()) {
                                val event = parseStreamEvent(data.toString())
                                if (event != null) {
                                    send(event)
                                    if (event is TotemChatStreamEvent.Finished) finished = true
                                }
                                data = StringBuilder()
                            }
                        }
                    }
                }
                if (data.isNotEmpty()) {
                    val event = parseStreamEvent(data.toString())
                    if (event != null) {
                        send(event)
                        if (event is TotemChatStreamEvent.Finished) finished = true
                    }
                }
                if (!finished) send(TotemChatStreamEvent.Finished)
            } finally {
                connection.disconnect()
            }
        }
    }

    suspend fun credits(): Long {
        val session = requireSession()
        val response = request("GET", "/credits", bearer = session)
        return response.optLong("balance", -1L)
    }

    suspend fun walletBalance(): TotemWalletBalance {
        val session = requireSession()
        val response = request("GET", "/wallet/balance", bearer = session)
        return TotemWalletBalance(
            available = response.optBoolean("available", false),
            noWallet = response.optBoolean("noWallet", false),
            amount = response.optString("amount").takeIf { it.isNotBlank() && it != "null" },
            symbol = response.optString("symbol").takeIf { it.isNotBlank() && it != "null" },
            chainId = if (response.has("chainId")) response.optInt("chainId") else null,
            error = response.optString("error").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    private suspend fun requireSession(): String =
        store.token()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Please sign in to your TOTEM account first.")

    private suspend fun request(
        method: String,
        path: String,
        bearer: String? = null,
        body: JSONObject? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(apiBase + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
            }
        }

        try {
            if (body != null) {
                connection.outputStream.use {
                    it.write(body.toString().toByteArray(Charsets.UTF_8))
                }
            }
            val status = connection.responseCode
            val raw = readStream(if (status in 200..299) connection.inputStream else connection.errorStream)
            if (status !in 200..299) {
                throw IllegalStateException(extractError(raw, status))
            }
            if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun readStream(stream: java.io.InputStream?): String =
        stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

    private fun extractError(raw: String, status: Int): String {
        val detail = runCatching {
            val json = JSONObject(raw)
            json.optString("error").takeIf { it.isNotBlank() }
                ?: json.optString("message").takeIf { it.isNotBlank() }
        }.getOrNull()
        return detail ?: "TOTEM request failed (HTTP $status)."
    }

    private fun parseStreamEvent(raw: String): TotemChatStreamEvent? {
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val type = json.optString("type")
        return when (type) {
            "textResponseChunk", "textResponse" -> {
                val text = json.optString("textResponse")
                if (text.isBlank()) null else TotemChatStreamEvent.Text(text)
            }
            "abort" -> TotemChatStreamEvent.Failed(
                json.optString("error").takeIf { it.isNotBlank() && it != "false" }
                    ?: "The TOTEM server stopped before completing the answer."
            )
            "finalizeResponseStream" -> TotemChatStreamEvent.Finished
            else -> {
                val text = json.optString("textResponse")
                if (text.isNotBlank()) TotemChatStreamEvent.Text(text) else null
            }
        }
    }

    private fun parseHistory(
        history: JSONArray?,
        threadKey: String
    ): List<TotemChatMessage> {
        if (history == null) return emptyList()
        return buildList {
            for (index in 0 until history.length()) {
                val item = history.optJSONObject(index) ?: continue
                val role = item.optString("role")
                val text = item.optString("content")
                if (text.isBlank()) continue
                when (role) {
                    "user" -> add(
                        TotemChatMessage(
                            id = "user-$threadKey-$index",
                            fromUser = true,
                            text = text
                        )
                    )
                    "assistant" -> add(
                        TotemChatMessage(
                            id = "assistant-$threadKey-$index",
                            fromUser = false,
                            text = text
                        )
                    )
                }
            }
        }
    }

    companion object {
        const val DEFAULT_THREAD = "default-thread"
    }
}
