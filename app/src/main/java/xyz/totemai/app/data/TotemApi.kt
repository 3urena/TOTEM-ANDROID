package xyz.totemai.app.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class ApiWorkspace(val id: String, val name: String, val slug: String)
data class ApiThread(val id: String, val name: String, val slug: String)
data class ApiMessage(val id: String, val role: String, val content: String)
data class CreditTransaction(val id: String, val type: String, val amount: Long, val createdAt: String)
data class CreditsSnapshot(val balance: Long, val transactions: List<CreditTransaction>)
data class WalletSnapshot(
    val available: Boolean,
    val noWallet: Boolean = false,
    val amount: String? = null,
    val symbol: String = "TOTEM",
    val error: String? = null
)
data class LoginResult(val token: String, val userJson: String?)

object TotemApi {
    private const val API_BASE = "https://beta.totemai.xyz/api"
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 60_000

    suspend fun login(identifier: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("password", password)
            if (identifier.isNotBlank()) body.put("username", identifier.trim())
            val response = requestJson("POST", "/request-token", body = body)
            if (!response.optBoolean("valid", false)) {
                throw ApiException(response.optString("message").ifBlank { "Login was rejected by TOTEM." })
            }
            val token = response.optString("token").takeIf { it.isNotBlank() }
                ?: throw ApiException("TOTEM did not return a session token.")
            val userJson = response.optJSONObject("user")?.toString()
                ?: if (identifier.isNotBlank()) JSONObject().put("username", identifier.trim()).toString() else null
            LoginResult(token, userJson)
        }

    suspend fun getWorkspaces(token: String): List<ApiWorkspace> =
        withContext(Dispatchers.IO) {
            val array = requestJson("GET", "/workspaces", token = token).optJSONArray("workspaces") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val slug = item.optString("slug").takeIf { it.isNotBlank() } ?: continue
                    add(ApiWorkspace(item.optString("id", slug), item.optString("name").ifBlank { "Workspace" }, slug))
                }
            }
        }

    suspend fun getThreads(token: String, workspaceSlug: String): List<ApiThread> =
        withContext(Dispatchers.IO) {
            val path = "/workspace/" + Uri.encode(workspaceSlug) + "/threads"
            val array = requestJson("GET", path, token = token).optJSONArray("threads") ?: JSONArray()
            buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    val slug = item.optString("slug").takeIf { it.isNotBlank() } ?: continue
                    add(ApiThread(item.optString("id", slug), item.optString("name").ifBlank { "New chat" }, slug))
                }
            }
        }

    suspend fun createThread(token: String, workspaceSlug: String): ApiThread =
        withContext(Dispatchers.IO) {
            val path = "/workspace/" + Uri.encode(workspaceSlug) + "/thread/new"
            val response = requestJson("POST", path, token = token, body = JSONObject())
            val thread = response.optJSONObject("thread") ?: throw ApiException("TOTEM did not create a new chat.")
            val slug = thread.optString("slug").takeIf { it.isNotBlank() }
                ?: throw ApiException("TOTEM returned a thread without an identifier.")
            ApiThread(thread.optString("id", slug), thread.optString("name").ifBlank { "New chat" }, slug)
        }

    suspend fun getChatHistory(token: String, workspaceSlug: String, threadSlug: String? = null): List<ApiMessage> =
        withContext(Dispatchers.IO) {
            val workspace = Uri.encode(workspaceSlug)
            val path = if (threadSlug.isNullOrBlank()) {
                "/workspace/" + workspace + "/chats"
            } else {
                "/workspace/" + workspace + "/thread/" + Uri.encode(threadSlug) + "/chats"
            }
            val history = requestJson("GET", path, token = token).optJSONArray("history") ?: JSONArray()
            val messages = mutableListOf<ApiMessage>()

            fun appendMessage(item: JSONObject) {
                val role = item.optString("role")
                val content = item.optString("content")
                if ((role == "user" || role == "assistant") && content.isNotBlank()) {
                    val id = item.optString("chatId").ifBlank { messages.size.toString() }
                    messages.add(ApiMessage(id + "-" + role, role, content))
                }
            }

            for (i in 0 until history.length()) {
                when (val item = history.opt(i)) {
                    is JSONObject -> appendMessage(item)
                    is JSONArray -> for (j in 0 until item.length()) item.optJSONObject(j)?.let(::appendMessage)
                }
            }
            messages
        }

    suspend fun streamChat(
        token: String,
        workspaceSlug: String,
        message: String,
        threadSlug: String? = null,
        onAssistantText: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val workspace = Uri.encode(workspaceSlug)
        val path = if (threadSlug.isNullOrBlank()) {
            "/workspace/" + workspace + "/stream-chat"
        } else {
            "/workspace/" + workspace + "/thread/" + Uri.encode(threadSlug) + "/stream-chat"
        }
        val connection = openConnection("POST", path, token, "text/event-stream")
        try {
            connection.doOutput = true
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(JSONObject().put("message", message).put("attachments", JSONArray()).toString())
            }

            val status = connection.responseCode
            if (status !in 200..299) {
                val raw = connection.errorStream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                throw ApiException(extractError(raw, "Chat request failed (HTTP " + status + ")."))
            }

            val answer = StringBuilder()
            BufferedReader(InputStreamReader(connection.inputStream, StandardCharsets.UTF_8)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) continue
                    val raw = line.removePrefix("data:").trim()
                    if (raw.isBlank() || raw == "[DONE]") continue
                    val event = runCatching { JSONObject(raw) }.getOrNull() ?: continue
                    when (event.optString("type")) {
                        "textResponseChunk", "textResponse" -> {
                            val chunk = event.optString("textResponse")
                            if (chunk.isNotEmpty()) {
                                answer.append(chunk)
                                val snapshot = answer.toString()
                                withContext(Dispatchers.Main) { onAssistantText(snapshot) }
                            }
                        }
                        "abort" -> {
                            val messageText = event.optString("error")
                                .ifBlank { event.optString("message") }
                                .ifBlank { "The TOTEM chat request was interrupted." }
                            throw ApiException(messageText)
                        }
                    }
                }
            }
            answer.toString()
        } finally {
            connection.disconnect()
        }
    }

    suspend fun getCredits(token: String): CreditsSnapshot =
        withContext(Dispatchers.IO) {
            val response = requestJson("GET", "/credits", token = token)
            val array = response.optJSONArray("transactions") ?: JSONArray()
            val transactions = buildList {
                for (i in 0 until array.length()) {
                    val item = array.optJSONObject(i) ?: continue
                    add(CreditTransaction(item.optString("id", i.toString()), item.optString("type"), item.optLong("amount"), item.optString("createdAt")))
                }
            }
            CreditsSnapshot(response.optLong("balance"), transactions)
        }

    suspend fun getWalletBalance(token: String): WalletSnapshot =
        withContext(Dispatchers.IO) {
            val response = requestJson("GET", "/wallet/balance", token = token)
            if (response.optBoolean("unavailable", false)) {
                return@withContext WalletSnapshot(false, error = "The wallet balance service is not enabled on this TOTEM server.")
            }
            if (response.optBoolean("available", false)) {
                return@withContext WalletSnapshot(
                    true,
                    amount = response.optString("amount"),
                    symbol = response.optString("symbol").ifBlank { "TOTEM" }
                )
            }
            WalletSnapshot(
                false,
                noWallet = response.optBoolean("noWallet", false),
                error = response.optString("error").takeIf { it.isNotBlank() }
            )
        }

    private fun requestJson(
        method: String,
        path: String,
        token: String? = null,
        body: JSONObject? = null
    ): JSONObject {
        val connection = openConnection(method, path, token)
        try {
            if (body != null) {
                connection.doOutput = true
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { it.write(body.toString()) }
            }
            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_NO_CONTENT) return JSONObject().put("unavailable", true)
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) throw ApiException(extractError(raw, "TOTEM request failed (HTTP " + status + ")."))
            return if (raw.isBlank()) JSONObject() else JSONObject(raw)
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        method: String,
        path: String,
        token: String? = null,
        accept: String = "application/json"
    ): HttpURLConnection {
        return (URL(API_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", accept)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Requested-With", "TOTEM-Android")
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer " + token)
        }
    }

    private fun extractError(raw: String, fallback: String): String {
        if (raw.isBlank()) return fallback
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return fallback
        return json.optString("message").ifBlank { json.optString("error").ifBlank { fallback } }
    }
}

class ApiException(message: String) : Exception(message)
