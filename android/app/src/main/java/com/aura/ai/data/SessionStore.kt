package com.aura.ai.data

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.aura.ai.BuildConfig

/** Stores the session token (encrypted), the server address and small app settings. */
class SessionStore(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("aura_settings", Context.MODE_PRIVATE)

    private val secure: SharedPreferences = try {
        val key = MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build()
        EncryptedSharedPreferences.create(
            context,
            "aura_secure",
            key,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    } catch (e: Exception) {
        // Some devices have a broken keystore; fall back to private app storage.
        context.getSharedPreferences("aura_secure_fallback", Context.MODE_PRIVATE)
    }

    var token: String?
        get() = secure.getString("token", null)
        set(value) = secure.edit().apply { if (value == null) remove("token") else putString("token", value) }.apply()

    var serverUrl: String
        get() = prefs.getString("server_url", null) ?: BuildConfig.DEFAULT_SERVER_URL
        set(value) = prefs.edit().putString("server_url", value.trim().trimEnd('/')).apply()

    var hapticsEnabled: Boolean
        get() = prefs.getBoolean("haptics", true)
        set(value) = prefs.edit().putBoolean("haptics", value).apply()

    var lastSyncAt: Long
        get() = prefs.getLong("last_sync", 0)
        set(value) = prefs.edit().putLong("last_sync", value).apply()

    var cachedAccount: Account?
        get() = prefs.getString("account", null)?.let { runCatching { AuraJson.decodeFromString<Account>(it) }.getOrNull() }
        set(value) = prefs.edit().apply {
            if (value == null) remove("account") else putString("account", AuraJson.encodeToString(Account.serializer(), value))
        }.apply()

    fun clearSession() {
        token = null
        cachedAccount = null
        lastSyncAt = 0
    }
}
