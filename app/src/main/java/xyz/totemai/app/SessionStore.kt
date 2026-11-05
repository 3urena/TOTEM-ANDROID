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
    private var memoryMobileDeviceToken: String? = null

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

    @Synchronized
    fun token(): String? = read(KEY_TOKEN) ?: memoryToken

    @Synchronized
    fun user(): String? = read(KEY_USER) ?: memoryUser

    @Synchronized
    fun saveMobileDeviceToken(token: String) {
        val p = prefs()
        if (p == null) {
            memoryMobileDeviceToken = token
            return
        }
        val saved = runCatching {
            p.edit().putString(KEY_MOBILE_DEVICE_TOKEN, token).apply()
        }.isSuccess
        if (saved) {
            memoryMobileDeviceToken = null
        } else {
            Log.w(TAG, "encrypted mobile-token write failed; using memory-only token")
            prefs = null
            memoryMobileDeviceToken = token
        }
    }

    @Synchronized
    fun mobileDeviceToken(): String? =
        read(KEY_MOBILE_DEVICE_TOKEN) ?: memoryMobileDeviceToken

    @Synchronized
    fun clearMobileDeviceToken() {
        memoryMobileDeviceToken = null
        val p = prefs() ?: return
        runCatching { p.edit().remove(KEY_MOBILE_DEVICE_TOKEN).apply() }
            .onFailure {
                Log.w(TAG, "encrypted mobile-token clear failed: ${it.javaClass.simpleName}")
                prefs = null
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
        memoryMobileDeviceToken = null
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
        const val KEY_TOKEN = TotemConfig.KEY_TOKEN
        const val KEY_USER = TotemConfig.KEY_USER
        private const val KEY_MOBILE_DEVICE_TOKEN = "totem_mobile_device_token"

        @Volatile
        private var instance: SessionStore? = null

        fun get(context: Context): SessionStore =
            instance ?: synchronized(this) {
                instance ?: SessionStore(context).also { instance = it }
            }
    }
}
