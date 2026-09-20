package settings

import android.content.Context

/**
 * The Telegram *application* identity, created at my.telegram.org. It
 * identifies the app, never the account: leaking it lets someone
 * impersonate this client, not sign in as anybody.
 *
 * [apiId] is a plain number and is not a secret. [apiHash] is, so
 * [toString] is overridden — the generated one would print it in full the
 * moment anything interpolates or logs this object.
 */
data class TelegramCredentials(val apiId: Int, val apiHash: String) {
    override fun toString(): String = "TelegramCredentials(apiId=$apiId, apiHash=<redacted>)"
}

interface TelegramSettings {
    suspend fun read(): TelegramCredentials?
    suspend fun write(apiId: Int, apiHash: String)
    suspend fun clear()
}

/** In-memory implementation for tests; nothing here ever touches disk. */
class InMemoryTelegramSettings : TelegramSettings {

    @Volatile
    private var stored: TelegramCredentials? = null

    override suspend fun read(): TelegramCredentials? = stored

    override suspend fun write(apiId: Int, apiHash: String) {
        stored = TelegramCredentials(apiId, apiHash)
    }

    override suspend fun clear() {
        stored = null
    }
}

/**
 * Persists the identity in its own keystore-backed preferences file,
 * under the same encryption the package key gets — the api hash is a
 * secret and is protected as one.
 *
 * A missing or non-positive [TelegramCredentials.apiId] reads back as no
 * credentials at all: a zero is what an absent int looks like, and a core
 * built on one would fail on its first call to Telegram rather than here.
 */
class EncryptedTelegramSettings(context: Context) : TelegramSettings {

    private val preferences = encryptedPreferences(context, PREFS_FILE_NAME)

    override suspend fun read(): TelegramCredentials? {
        val apiId = preferences.getInt(KEY_API_ID, 0).takeIf { it > 0 } ?: return null
        val apiHash = preferences.getString(KEY_API_HASH, null)?.takeIf(String::isNotBlank) ?: return null
        return TelegramCredentials(apiId, apiHash)
    }

    override suspend fun write(apiId: Int, apiHash: String) {
        preferences.edit()
            .putInt(KEY_API_ID, apiId)
            .putString(KEY_API_HASH, apiHash)
            .apply()
    }

    override suspend fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "telegram_settings"
        const val KEY_API_ID = "api_id"
        const val KEY_API_HASH = "api_hash"
    }
}
