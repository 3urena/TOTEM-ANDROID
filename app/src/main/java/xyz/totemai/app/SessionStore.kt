package xyz.totemai.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/** Stores the hosted-platform session using Android Keystore-backed encryption. */
class SessionStore private constructor(context: Context) {
    private val appContext: Context = context.applicationContext
    private var prefs: SharedPreferences? = null
    private var opened = false
    private var memoryToken: String? = null
    private var memoryUser: String? = null
    private var memoryDeviceToken: String? = null
    private var memoryLastWorkspace: String? = null
    private var memoryLastThread: String? = null

    @Synchronized
    private fun prefs(): SharedPreferences? {
        if (opened) return prefs
        opened = true
        prefs = try {
            val key = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                FILE,
                key,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (t: Throwable) {
            Log.w(TAG, "encrypted storage unavailable: ${t.javaClass.simpleName}")
            null
        }
        return prefs
    }

    val persisted: Boolean
        @Synchronized get() = prefs() != null

    @Synchronized
    fun save(token: String, user: String?) {
        val previousToken = token()
        if (!previousToken.isNullOrBlank() && previousToken != token) {
            clearDeviceToken()
            clearActiveChat()
        }
        val p = prefs()
        if (p == null) {
            memoryToken = token
            memoryUser = user
            return
        }

        val saved = runCatching {
            val editor = p.edit().putString(KEY_TOKEN, token)
            if (user.isNullOrEmpty()) editor.remove(KEY_USER) else editor.putString(KEY_USER, user)
            editor.apply()
        }.isSuccess

        if (saved) {
            memoryToken = null
            memoryUser = null
        } else {
            Log.w(TAG, "encrypted session write failed; using memory-only session")
            prefs = null
            memoryToken = token
            memoryUser = user
        }
    }

    /** Device-local appearance preference; it survives account sign-out. */
    @Synchronized
    fun themeMode(): String {
        val saved = runCatching {
            appContext.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
                .getString(KEY_THEME_MODE, DEFAULT_THEME_MODE)
        }.getOrNull()
        return saved?.takeIf { it in setOf("system", "light", "dark") } ?: DEFAULT_THEME_MODE
    }

    @Synchronized
    fun saveThemeMode(mode: String) {
        val safeMode = mode.takeIf { it in setOf("system", "light", "dark") }
            ?: DEFAULT_THEME_MODE
        runCatching {
            appContext.getSharedPreferences(PREFERENCES_FILE, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_THEME_MODE, safeMode)
                .apply()
        }.onFailure {
            Log.w(TAG, "theme preference write failed: ${it.javaClass.simpleName}")
        }
    }

    @Synchronized
    fun token(): String? = read(KEY_TOKEN) ?: memoryToken

    @Synchronized
    fun user(): String? = read(KEY_USER) ?: memoryUser

    @Synchronized
    fun deviceToken(): String? = read(KEY_DEVICE_TOKEN) ?: memoryDeviceToken

    @Synchronized
    fun saveDeviceToken(token: String) {
        val p = prefs()
        if (p == null) {
            memoryDeviceToken = token
            return
        }
        runCatching { p.edit().putString(KEY_DEVICE_TOKEN, token).apply() }
            .onSuccess { memoryDeviceToken = null }
            .onFailure {
                Log.w(TAG, "encrypted device-token write failed; using memory-only token")
                prefs = null
                memoryDeviceToken = token
            }
    }

    @Synchronized
    fun clearDeviceToken() {
        memoryDeviceToken = null
        runCatching { prefs()?.edit()?.remove(KEY_DEVICE_TOKEN)?.apply() }
    }

    /** Saves the last workspace/thread so a new app process can restore the conversation. */
    @Synchronized
    fun saveActiveChat(workspaceSlug: String, threadSlug: String) {
        memoryLastWorkspace = workspaceSlug
        memoryLastThread = threadSlug
        val p = prefs() ?: return
        runCatching {
            p.edit()
                .putString(KEY_LAST_WORKSPACE, workspaceSlug)
                .putString(KEY_LAST_THREAD, threadSlug)
                .apply()
        }.onFailure {
            Log.w(TAG, "active chat write failed: ${it.javaClass.simpleName}")
            prefs = null
        }
    }

    @Synchronized
    fun activeChat(): Pair<String?, String?> {
        val p = prefs()
        val workspace = runCatching { p?.getString(KEY_LAST_WORKSPACE, null) }.getOrNull()
            ?: memoryLastWorkspace
        val thread = runCatching { p?.getString(KEY_LAST_THREAD, null) }.getOrNull()
            ?: memoryLastThread
        return workspace to thread
    }

    @Synchronized
    fun clearActiveChat() {
        memoryLastWorkspace = null
        memoryLastThread = null
        runCatching {
            prefs()?.edit()
                ?.remove(KEY_LAST_WORKSPACE)
                ?.remove(KEY_LAST_THREAD)
                ?.apply()
        }
    }

    private fun read(key: String): String? {
        val p = prefs() ?: return null
        return runCatching { p.getString(key, null) }
            .onFailure {
                Log.w(TAG, "encrypted session read failed: ${it.javaClass.simpleName}")
                prefs = null
            }
            .getOrNull()
    }

    @Synchronized
    fun clear() {
        memoryToken = null
        memoryUser = null
        memoryDeviceToken = null
        memoryLastWorkspace = null
        memoryLastThread = null
        val p = prefs() ?: return
        runCatching { p.edit().clear().apply() }
            .onFailure {
                Log.w(TAG, "encrypted session clear failed: ${it.javaClass.simpleName}")
                prefs = null
            }
    }

    fun signedIn(): Boolean = !token().isNullOrEmpty()

    companion object {
        private const val TAG = "TotemSession"
        private const val FILE = "totem_session"
        private const val PREFERENCES_FILE = "totem_preferences"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val DEFAULT_THEME_MODE = "dark"
        const val KEY_TOKEN = TotemConfig.KEY_TOKEN
        const val KEY_USER = TotemConfig.KEY_USER
        private const val KEY_DEVICE_TOKEN = "totem_mobile_device_token"
        private const val KEY_LAST_WORKSPACE = "totem_last_workspace"
        private const val KEY_LAST_THREAD = "totem_last_thread"

        @Volatile
        private var instance: SessionStore? = null

        fun get(context: Context): SessionStore =
            instance ?: synchronized(this) {
                instance ?: SessionStore(context).also { instance = it }
            }
    }
}
