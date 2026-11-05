package xyz.totemai.app

import android.net.Uri
import android.os.Build
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

sealed interface TotemChatStreamEvent {
    data class Text(val value: String) : TotemChatStreamEvent
    data class Failed(val message: String) : TotemChatStreamEvent
    data object Finished : TotemChatStreamEvent
}

/**
 * Thin Android client for the official TOTEM hosted API.
 *
 * Chat commands use the server-issued mobile-device token. Account data such
 * as credits and wallet balance use the signed-in account session token.
 */
class TotemApi(private val store: SessionStore) {
    private val apiBase = TotemConfig.ORIGIN + "/api"

    suspend fun workspaces(): List<TotemWorkspace> {
        val token = ensureDeviceToken()
        val response = request(
            method = "POST",
            path = "/mobile/send/workspaces",
            deviceToken = token,
            body = JSONObject()
        )
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
        val token = ensureDeviceToken()
        val response = request(
            method = "POST",
            path = "/mobile/send/workspace-content",
            deviceToken = token,
            body = JSONObject().put("workspaceSlug", workspaceSlug)
        )
        val rawThreads = response.optJSONArray("threads") ?: JSONArray()
        val rawChats = response.optJSONArray("chats") ?: JSONArray()

        data class ThreadRow(val id: Long, val slug: String, val name: String)
        val threadRows = ArrayList<ThreadRow>()
        for (index in 0 until rawThreads.length()) {
            val item = rawThreads.optJSONObject(index) ?: continue
            val slug = item.optString("slug")
            if (slug.isBlank()) continue
            threadRows.add(
                ThreadRow(
                    id = item.optLong("id", 0L),
                    slug = slug,
                    name = item.optString("name", "Chat")
                )
            )
        }
        if (threadRows.none { it.slug == DEFAULT_THREAD }) {
            threadRows.add(0, ThreadRow(0L, DEFAULT_THREAD, "Default Thread"))
        }

        val messages = LinkedHashMap<String, MutableList<TotemChatMessage>>()
        val firstPrompts = HashMap<String, String>()
        val slugById = threadRows.associate { it.id to it.slug }

        for (index in 0 until rawChats.length()) {
            val item = rawChats.optJSONObject(index) ?: continue
            val threadId = if (item.isNull("thread_id")) 0L else item.optLong("thread_id", 0L)
            val slug = slugById[threadId] ?: DEFAULT_THREAD
            val bucket = messages.getOrPut(slug) { ArrayList() }
            val prompt = item.optString("prompt")
            if (prompt.isNotBlank()) {
                bucket.add(
                    TotemChatMessage(
                        id = "u-${item.optString("id", index.toString())}",
                        fromUser = true,
                        text = prompt
                    )
                )
                if (!firstPrompts.containsKey(slug)) firstPrompts[slug] = prompt
            }

            val responseText = responseText(item.opt("response"))
            if (responseText.isNotBlank()) {
                bucket.add(
                    TotemChatMessage(
                        id = "a-${item.optString("id", index.toString())}",
                        fromUser = false,
                        text = responseText
                    )
                )
            }
        }

        val threads = threadRows.map { thread ->
            val preview = firstPrompts[thread.slug].orEmpty()
            val title = if (preview.isNotBlank()) preview.take(44) else thread.name
            TotemChatThread(thread.id, thread.slug, title, preview)
        }.sortedByDescending { it.id }

        return TotemWorkspaceSnapshot(
            threads = threads,
            messagesByThread = messages.mapValues { it.value.toList() }
        )
    }

    suspend fun newThread(workspaceSlug: String): TotemChatThread {
        val token = ensureDeviceToken()
        val response = request(
            method = "POST",
            path = "/mobile/send/new-thread",
            deviceToken = token,
            body = JSONObject().put("workspaceSlug", workspaceSlug)
        )
        val thread = response.optJSONObject("thread")
            ?: throw IllegalStateException("The server did not return the new chat.")
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
        val token = ensureDeviceToken()
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("workspaceSlug", workspaceSlug)
                .put("message", message)
            if (threadSlug != DEFAULT_THREAD) body.put("threadSlug", threadSlug)

            val connection = (URL(apiBase + "/mobile/send/stream-chat")
                .openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 180_000
                doOutput = true
                setRequestProperty("Accept", "text/event-stream")
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Cache-Control", "no-cache")
                setRequestProperty(DEVICE_TOKEN_HEADER, token)
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

    private suspend fun ensureDeviceToken(): String {
        val saved = store.deviceToken()
        if (!saved.isNullOrBlank()) {
            val valid = runCatching {
                request("GET", "/mobile/auth", deviceToken = saved)
            }.isSuccess
            if (valid) return saved
            store.clearDeviceToken()
        }

        val session = requireSession()
        val connectionInfo = request(
            method = "GET",
            path = "/mobile/connect-info",
            bearer = session
        )
        val connectionUrl = connectionInfo.optString("connectionUrl")
        if (connectionUrl.isBlank()) {
            throw IllegalStateException("The TOTEM server did not provide a mobile registration link.")
        }

        val connectionUri = if (connectionUrl.startsWith("http")) {
            Uri.parse(connectionUrl)
        } else {
            Uri.parse(TotemConfig.ORIGIN + connectionUrl)
        }
        val temporaryToken = connectionUri.getQueryParameter("t")
            ?: throw IllegalStateException("The mobile registration link is missing its temporary token.")

        val deviceName = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android device"
        val registered = request(
            method = "POST",
            path = "/mobile/register",
            bearer = temporaryToken,
            body = JSONObject()
                .put("deviceOs", "android")
                .put("deviceName", deviceName)
        )
        val deviceToken = registered.optString("token")
        if (deviceToken.isBlank()) {
            throw IllegalStateException("The TOTEM server did not return a device token.")
        }

        // A newly registered device starts unapproved. The same authenticated
        // admin session that issued connect-info approves this device.
        val devicesResponse = request(
            method = "GET",
            path = "/mobile/devices",
            bearer = session
        )
        val devices = devicesResponse.optJSONArray("devices") ?: JSONArray()
        var deviceId: Long? = null
        var alreadyApproved = false
        for (index in 0 until devices.length()) {
            val device = devices.optJSONObject(index) ?: continue
            if (device.optString("deviceName") == deviceName &&
                device.optString("token") == deviceToken
            ) {
                deviceId = device.optLong("id")
                alreadyApproved = device.optBoolean("approved", false)
                break
            }
        }
        if (deviceId == null) {
            throw IllegalStateException("The new device is registered but could not be found for approval.")
        }
        if (!alreadyApproved) {
            request(
                method = "POST",
                path = "/mobile/update/$deviceId",
                bearer = session,
                body = JSONObject().put("approved", true)
            )
        }

        request("GET", "/mobile/auth", deviceToken = deviceToken)
        store.saveDeviceToken(deviceToken)
        return deviceToken
    }

    private suspend fun requireSession(): String =
        store.token()?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Please sign in to your TOTEM account first.")

    private suspend fun request(
        method: String,
        path: String,
        bearer: String? = null,
        deviceToken: String? = null,
        body: JSONObject? = null
    ): JSONObject = withContext(Dispatchers.IO) {
        val connection = (URL(apiBase + path).openConnection() as HttpURLConnection).apply {
            requestMethod = method
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Accept", "application/json")
            bearer?.let { setRequestProperty("Authorization", "Bearer $it") }
            deviceToken?.let { setRequestProperty(DEVICE_TOKEN_HEADER, it) }
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

    private fun responseText(value: Any?): String {
        return when (value) {
            is JSONObject -> value.optString("text").takeIf { it.isNotBlank() }
                ?: value.optString("textResponse")
            is String -> runCatching {
                val json = JSONObject(value)
                json.optString("text").takeIf { it.isNotBlank() }
                    ?: json.optString("textResponse")
            }.getOrDefault(value)
            else -> ""
        }.orEmpty()
    }

    companion object {
        const val DEFAULT_THREAD = "default-thread"
        private const val DEVICE_TOKEN_HEADER = "x-anythingllm-mobile-device-token"
    }
}
