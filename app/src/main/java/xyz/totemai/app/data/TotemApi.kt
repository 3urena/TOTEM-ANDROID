package xyz.totemai.app.data

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import xyz.totemai.app.TotemConfig
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

data class ApiWorkspace(
    val id: String,
    val name: String,
    val slug: String
)

data class ApiMessage(
    val id: String,
    val role: String,
    val content: String
)

data class CreditTransaction(
    val id: String,
    val type: String,
    val amount: Long,
    val createdAt: String
)

data class CreditsSnapshot(
    val balance: Long,
    val transactions: List<CreditTransaction>
)

data class WalletSnapshot(
    val available: Boolean,
    val noWallet: Boolean = false,
    val amount: String? = null,
    val symbol: String = "TOTEM",
    val error: String? = null
)

data class LoginResult(
    val token: String,
    val userJson: String?
)

object TotemApi {
    private const val CONNECT_TIMEOUT_MS = 20_000
    private const val READ_TIMEOUT_MS = 60_000
    private const val API_BASE = "https://beta.totemai.xyz/api"

    suspend fun login(identifier: String, password: String): LoginResult =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("password", password)
            if (identifier.isNotBlank()) body.put("username", identifier.trim())

            val response = requestJson(
                method = "POST",
                path = "/request-token",
                body = body
            )
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
            val response = requestJson("GET", "/workspaces", token = token)
            val array = response.optJSONArray("workspaces") ?: JSONArray()
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val slug = item.optString("slug").takeIf { it.isNotBlank() } ?: continue
                    add(
                        ApiWorkspace(
                            id = item.optString("id", slug),
                            name = item.optString("name").ifBlank { "Workspace" },
                            slug = slug
                        )
                    )
                }
            }
        }

    suspend fun getChatHistory(token: String, workspaceSlug: String): List<ApiMessage> =
        withContext(Dispatchers.IO) {
            val encodedSlug = Uri.encode(workspaceSlug)
            val response = requestJson("GET", "/workspace/" + encodedSlug + "/chats", token = token)
            val history = response.optJSONArray("history") ?: JSONArray()
            val messages = mutableListOf<ApiMessage>()

            fun addMessage(item: JSONObject) {
                val role = item.optString("role")
                val content = item.optString("content")
                if ((role == "user" || role == "assistant") && content.isNotBlank()) {
                    val rawId = item.optString("chatId").ifBlank { messages.size.toString() }
                    messages.add(ApiMessage(rawId + "-" + role, role, content))
                }
            }

            for (index in 0 until history.length()) {
                when (val item = history.opt(index)) {
                    is JSONObject -> addMessage(item)
                    is JSONArray -> {
                        for (innerIndex in 0 until item.length()) {
                            item.optJSONObject(innerIndex)?.let(::addMessage)
                        }
                    }
                }
            }
            messages
        }

    suspend fun streamChat(
        token: String,
        workspaceSlug: String,
        message: String,
        onAssistantText: (String) -> Unit
    ): String = withContext(Dispatchers.IO) {
        val connection = openConnection(
            method = "POST",
            path = "/workspace/" + Uri.encode(workspaceSlug) + "/stream-chat",
            token = token,
            accept = "text/event-stream"
        )

        try {
            connection.doOutput = true
            OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                writer.write(JSONObject().put("message", message).put("attachments", JSONArray()).toString())
                writer.flush()
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

                    val rawData = line.removePrefix("data:").trim()
                    if (rawData.isBlank() || rawData == "[DONE]") continue

                    val event = runCatching { JSONObject(rawData) }.getOrNull() ?: continue
                    when (event.optString("type")) {
                        "textResponseChunk" -> {
                            val chunk = event.optString("textResponse")
                            if (chunk.isNotEmpty()) {
                                answer.append(chunk)
                                val snapshot = answer.toString()
                                withContext(Dispatchers.Main) { onAssistantText(snapshot) }
                            }
                        }
                        "textResponse" -> {
                            val text = event.optString("textResponse")
                            if (text.isNotEmpty()) {
                                answer.append(text)
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
            val transactionsJson = response.optJSONArray("transactions") ?: JSONArray()
            val transactions = buildList {
                for (index in 0 until transactionsJson.length()) {
                    val item = transactionsJson.optJSONObject(index) ?: continue
                    add(
                        CreditTransaction(
                            id = item.optString("id", index.toString()),
                            type = item.optString("type").ifBlank { "transaction" },
                            amount = item.optLong("amount", 0L),
                            createdAt = item.optString("createdAt")
                        )
                    )
                }
            }
            CreditsSnapshot(
                balance = response.optLong("balance", 0L),
                transactions = transactions
            )
        }

    suspend fun getWalletBalance(token: String): WalletSnapshot =
        withContext(Dispatchers.IO) {
            val response = requestJson("GET", "/wallet/balance", token = token)
            if (response.optBoolean("unavailable", false)) {
                return@withContext WalletSnapshot(available = false, error = "The wallet balance service is not enabled on this TOTEM server.")
            }
            if (response.optBoolean("available", false)) {
                return@withContext WalletSnapshot(
                    available = true,
                    amount = response.optString("amount"),
                    symbol = response.optString("symbol").ifBlank { "TOTEM" }
                )
            }
            WalletSnapshot(
                available = false,
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
                OutputStreamWriter(connection.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(body.toString())
                    writer.flush()
                }
            }

            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_NO_CONTENT) {
                return JSONObject().put("unavailable", true)
            }

            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val raw = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                throw ApiException(extractError(raw, "TOTEM request failed (HTTP " + status + ")."))
            }
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
        val connection = (URL(API_BASE + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", accept)
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("X-Requested-With", "TOTEM-Android")
            if (!token.isNullOrBlank()) {
                setRequestProperty("Authorization", "Bearer " + token)
            }
        }
        return connection
    }

    private fun extractError(raw: String, fallback: String): String {
        if (raw.isBlank()) return fallback
        val json = runCatching { JSONObject(raw) }.getOrNull() ?: return fallback
        return json.optString("message").ifBlank {
            json.optString("error").ifBlank { fallback }
        }
    }
}

class ApiException(message: String) : Exception(message)
