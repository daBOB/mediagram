package settings

import android.content.Context

/**
 * The pairing token for a home `mediagram_cache` server: a plain secret
 * string, stored the same way [PackageSettings]' key is. This module never
 * learns the LAN chunk protocol the token signs — that lives entirely in
 * `:core:playback`, which reads this store the way it reads every other
 * credential.
 */
interface LanCacheTokenSettings {
    suspend fun read(): String?

    suspend fun write(token: String)

    suspend fun clear()
}

/** In-memory implementation for tests; nothing here ever touches disk. */
class InMemoryLanCacheTokenSettings : LanCacheTokenSettings {
    @Volatile
    private var stored: String? = null

    override suspend fun read(): String? = stored

    override suspend fun write(token: String) {
        stored = token
    }

    override suspend fun clear() {
        stored = null
    }
}

/**
 * Persists the token in `EncryptedSharedPreferences`, its own file — see
 * [EncryptedTelegramSettings] for why the store is opened lazily rather
 * than in the constructor.
 */
class EncryptedLanCacheTokenSettings(
    private val context: Context,
) : LanCacheTokenSettings {
    private val preferences by lazy { encryptedPreferences(context, PREFS_FILE_NAME) }

    override suspend fun read(): String? = preferences.getString(KEY_TOKEN, null)

    override suspend fun write(token: String) {
        preferences.edit().putString(KEY_TOKEN, token).apply()
    }

    override suspend fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "lan_cache_token_settings"
        const val KEY_TOKEN = "lan_cache_token"
    }
}
