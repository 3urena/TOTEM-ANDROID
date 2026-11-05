package xyz.totemai.app.api

import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import xyz.totemai.app.SessionStore
import xyz.totemai.app.TotemConfig
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

data class TotemWorkspace(
    val id: Int,
    val name: String,
    val slug: String,
    val threadCount: Int,
    val chatCount: Int
)

data class TotemThread(
    val id: Int,
    val name: String,
    val slug: String,
    val createdAt: String,
    val lastUpdatedAt: String
)

data class TotemChatRecord(
    val id: Int,
    val threadId: Int,
    val prompt: String,
    val response: String,
    val createdAt: String
)

data class TotemWorkspaceContent(
    val threads: List<TotemThread>,
    val chats: List<TotemChatRecord>
)

data class TotemLoginResult(
    val token: String,
    val userJson: String?,
    val recoveryCodes: List<String>
)

class TotemApi(private val store: SessionStore) {

    suspend fun login(identifier: String, password: String): TotemLoginResult {
        if (password.isBlank()) throw TotemApiException(0, "Password is required.")
        val body = JSONObject().put("password", password)
        if (identifier.isNotBlank()) body.put("username", identifier.trim())
        val result = requestJson("/api/request-token", "POST", body = body)
        if (!result.optBoolean("valid")) {
            throw TotemApiException(
                401,
                result.optString("message").takeIf { it.isNotBlank() }
                    ?: "The login details were not accepted."
            )
        }
        val token = result.optString("token")
        if (token.isBlank()) throw TotemApiException(500, "The server returned no session token.")
        val recoveryCodes = result.optJSONArray("recoveryCodes").toStringList()
        return TotemLoginResult(token, result.optJSONObject("user")?.toString(), recoveryCodes)
    }

    /**
     * Establishes the official mobile-device session. The Hosted API issues a
     * short-lived registration token, creates this Android device, and allows
     * the authenticated admin/single-user account to approve it.
     */
    suspend fun ensureMobileDevice(): String {
        val saved = store.mobileDeviceToken()
        if (!saved.isNullOrBlank()) {
            try {
                requestJson(
                    "/api/mobile/auth",
                    method = "GET",
                    mobileToken = saved
                )
                return saved
            } catch (error: TotemApiException) {
                if (error.statusCode == 400 &&
                    error.message.contains("not approved", ignoreCase = true)
                ) {
                    throw TotemApiException(
                        error.statusCode,
                        "This device is waiting for approval in TOTEM Mobile Connections."
                    )
                }
                store.clearMobileDeviceToken()
            }
        }

        val session = store.token()
            ?: throw TotemApiException(401, "Sign in to TOTEM before connecting this device.")

        val connectInfo = requestJson(
            "/api/mobile/connect-info",
            method = "GET",
            bearerToken = session
        )
        val connectionUrl = connectInfo.optString("connectionUrl")
        val registrationToken = Uri.parse(connectionUrl).getQueryParameter("t")
            ?: throw TotemApiException(500, "TOTEM did not return a mobile registration token.")

        val registerBody = JSONObject()
            .put("deviceOs", "android")
            .put("deviceName", Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android device")
        val registration = requestJson(
            "/api/mobile/register",
            method = "POST",
            body = registerBody,
            bearerToken = registrationToken
        )
        val deviceToken = registration.optString("token")
        if (deviceToken.isBlank()) {
            throw TotemApiException(500, "TOTEM did not return a device token.")
        }

        // The mobile API only exposes self-approval through admin-authorized
        // endpoints. This will fail safely for accounts without admin rights.
        val devicesResult = requestJson(
            "/api/mobile/devices",
            method = "GET",
            bearerToken = session
        )
        val devices = devicesResult.optJSONArray("devices") ?: JSONArray()
        var matchingDevice: JSONObject? = null
        for (index in 0 until devices.length()) {
            val candidate = devices.optJSONObject(index) ?: continue
            if (candidate.optString("token") == deviceToken) {
                matchingDevice = candidate
                break
            }
        }
        val deviceId = matchingDevice?.optInt("id", -1) ?: -1
        if (deviceId <= 0) {
            throw TotemApiException(
                403,
                "The device was registered but could not be approved. An account admin must approve it in Mobile Connections."
            )
        }
        if (matchingDevice?.optBoolean("approved") != true) {
            requestJson(
                "/api/mobile/update/$deviceId",
                method = "POST",
                body = JSONObject().put("approved", true),
                bearerToken = session
            )
        }
        requestJson("/api/mobile/auth", method = "GET", mobileToken = deviceToken)
        store.saveMobileDeviceToken(deviceToken)
        return deviceToken
    }

    suspend fun workspaces(): List<TotemWorkspace> {
        val result = mobileCommand("workspaces", JSONObject())
        val items = result.optJSONArray("workspaces") ?: JSONArray()
        return buildList {
            for (index in 0 until items.length()) {
                val item = items.optJSONObject(index) ?: continue
                val slug = item.optString("slug")
                if (slug.isBlank()) continue
                add(
                    TotemWorkspace(
                        id = item.optInt("id"),
                        name = item.optString("name").ifBlank { slug },
                        slug = slug,
                        threadCount = item.optInt("threadCount"),
                        chatCount = item.optInt("chatCount")
                    )
                )
            }
        }
    }

    suspend fun workspaceContent(workspaceSlug: String): TotemWorkspaceContent {
        val result = mobileCommand(
            "workspace-content",
            JSONObject().put("workspaceSlug", workspaceSlug)
        )
        val threadArray = result.optJSONArray("threads") ?: JSONArray()
        val chatArray = result.optJSONArray("chats") ?: JSONArray()
        val threads = buildList {
            for (index in 0 until threadArray.length()) {
                val item = threadArray.optJSONObject(index) ?: continue
                val slug = item.optString("slug")
                if (slug.isBlank()) continue
                add(
                    TotemThread(
                        id = item.optInt("id"),
                        name = item.optString("name").ifBlank { "Thread" },
                        slug = slug,
                        createdAt = item.optString("createdAt"),
                        lastUpdatedAt = item.optString("lastUpdatedAt")
                    )
                )
            }
        }
        val chats = buildList {
            for (index in 0 until chatArray.length()) {
                val item = chatArray.optJSONObject(index) ?: continue
                val rawResponse = item.opt("response")
                val responseObject = when (rawResponse) {
                    is JSONObject -> rawResponse
                    is String -> runCatching { JSONObject(rawResponse) }.getOrNull()
                    else -> null
                }
                add(
                    TotemChatRecord(
                        id = item.optInt("id"),
                        threadId = item.optInt("thread_id", 0),
                        prompt = item.optString("prompt"),
                        response = responseObject?.optString("text").orEmpty(),
                        createdAt = item.optString("createdAt")
                    )
                )
            }
        }.sortedBy { it.id }
        return TotemWorkspaceContent(threads, chats)
    }

    suspend fun createThread(workspaceSlug: String): TotemThread {
        val result = mobileCommand(
            "new-thread",
            JSONObject().put("workspaceSlug", workspaceSlug)
        )
        val thread = result.optJSONObject("thread")
            ?: throw TotemApiException(500, "TOTEM did not create a new conversation.")
        val slug = thread.optString("slug")
        if (slug.isBlank()) throw TotemApiException(500, "The new conversation has no thread ID.")
        return TotemThread(
            id = thread.optInt("id"),
            name = thread.optString("name").ifBlank { "New chat" },
            slug = slug,
            createdAt = thread.optString("createdAt"),
            lastUpdatedAt = thread.optString("lastUpdatedAt")
        )
    }

    suspend fun modelTag(workspaceSlug: String): String? {
        val result = mobileCommand(
            "model-tag",
            JSONObject().put("workspaceSlug", workspaceSlug)
        )
        return result.optString("model").takeIf { it.isNotBlank() }
    }

    suspend fun sendMessage(
        workspaceSlug: String,
        threadSlug: String,
        message: String,
        onDelta: suspend (String) -> Unit
    ): String {
        val deviceToken = store.mobileDeviceToken()
            ?: throw TotemApiException(401, "This device is not connected to TOTEM.")
        return withContext(Dispatchers.IO) {
            val connection = openConnection(
                "/api/mobile/send/stream-chat",
                method = "POST",
                mobileToken = deviceToken,
                body = JSONObject()
                    .put("workspaceSlug", workspaceSlug)
                    .put("threadSlug", threadSlug)
                    .put("message", message)
            )
            try {
                val status = connection.responseCode
                if (status !in 200..299) {
                    val errorBody = readResponse(connection, status)
                    throw TotemApiException(status, extractError(errorBody, status))
                }
                val fullText = StringBuilder()
                var serverError: String? = null
                connection.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.removePrefix("data:").trim()
                        if (data.isBlank()) continue
                        val chunk = runCatching { JSONObject(data) }.getOrNull() ?: continue
                        when (chunk.optString("type")) {
                            "textResponseChunk", "textResponse" -> {
                                val text = chunk.optString("textResponse")
                                if (text.isNotEmpty()) {
                                    fullText.append(text)
                                    withContext(Dispatchers.Main) { onDelta(text) }
                                }
                            }
                            "abort" -> {
                                val value = chunk.opt("error")
                                if (value is String && value.isNotBlank()) serverError = value
                            }
                            "finalizeResponseStream" -> {
                                if (fullText.isEmpty()) {
                                    val finalText = chunk.optString("textResponse")
                                    if (finalText.isNotEmpty()) {
                                        fullText.append(finalText)
                                        withContext(Dispatchers.Main) { onDelta(finalText) }
                                    }
                                }
                            }
                        }
                    }
                }
                if (!serverError.isNullOrBlank()) {
                    throw TotemApiException(502, serverError!!)
                }
                if (fullText.isBlank()) {
                    throw TotemApiException(
                        502,
                        "TOTEM returned an empty response. Check the selected model and try again."
                    )
                }
                fullText.toString()
            } finally {
                connection.disconnect()
            }
        }
    }

    private suspend fun mobileCommand(command: String, body: JSONObject): JSONObject {
        val token = store.mobileDeviceToken()
            ?: throw TotemApiException(401, "This device is not connected to TOTEM.")
        return requestJson(
            "/api/mobile/send/$command",
            method = "POST",
            body = body,
            mobileToken = token
        )
    }

    suspend fun credits(): JSONObject {
        val token = store.token() ?: throw TotemApiException(401, "Sign in to view Credits.")
        return requestJson("/api/credits", method = "GET", bearerToken = token)
    }

    suspend fun walletBalance(): JSONObject {
        val token = store.token() ?: throw TotemApiException(401, "Sign in to view your wallet.")
        return requestJson("/api/wallet/balance", method = "GET", bearerToken = token)
    }

    private suspend fun requestJson(
        path: String,
        method: String = "GET",
        body: JSONObject? = null,
        bearerToken: String? = null,
        mobileToken: String? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = openConnection(path, method, body, bearerToken, mobileToken)
        try {
            val status = connection.responseCode
            val responseText = readResponse(connection, status)
            if (status !in 200..299) {
                throw TotemApiException(status, extractError(responseText, status))
            }
            if (status == HttpURLConnection.HTTP_NO_CONTENT || responseText.isBlank()) {
                JSONObject()
            } else {
                runCatching { JSONObject(responseText) }.getOrElse {
                    throw TotemApiException(status, "TOTEM returned an invalid JSON response.")
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(
        path: String,
        method: String,
        body: JSONObject? = null,
        bearerToken: String? = null,
        mobileToken: String? = null
    ): HttpURLConnection {
        val connection = (URL(TotemConfig.ORIGIN + path).openConnection() as HttpURLConnection)
        connection.requestMethod = method
        connection.connectTimeout = CONNECT_TIMEOUT_MS
        connection.readTimeout = if (path.endsWith("/stream-chat")) STREAM_TIMEOUT_MS else CONNECT_TIMEOUT_MS
        connection.setRequestProperty("Accept", if (path.endsWith("/stream-chat")) "text/event-stream" else "application/json")
        connection.setRequestProperty("Cache-Control", "no-cache")
        if (!bearerToken.isNullOrBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $bearerToken")
        }
        if (!mobileToken.isNullOrBlank()) {
            connection.setRequestProperty("x-anythingllm-mobile-device-token", mobileToken)
        }
        if (body != null) {
            connection.doOutput = true
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        return connection
    }

    private fun readResponse(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) connection.inputStream else connection.errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private fun extractError(raw: String, status: Int): String {
        if (raw.isBlank()) return "TOTEM request failed (HTTP $status)."
        val parsed = runCatching { JSONObject(raw) }.getOrNull()
        return parsed?.optString("error")?.takeIf { it.isNotBlank() }
            ?: parsed?.optString("message")?.takeIf { it.isNotBlank() }
            ?: "TOTEM request failed (HTTP $status)."
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index).takeIf { it.isNotBlank() } ?: continue
                add(value)
            }
        }
    }

    companion object {
        private const val CONNECT_TIMEOUT_MS = 25_000
        private const val STREAM_TIMEOUT_MS = 240_000
    }
}

class TotemApiException(
    val statusCode: Int,
    override val message: String
) : IOException(message)
