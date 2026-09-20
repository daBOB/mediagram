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

/**
 * What counts as a stored identity, for every store alike.
 *
 * A zero api id is what an absent integer looks like coming back out of
 * preferences, and a core built on one would fail on its first call to
 * Telegram rather than here. Shared rather than repeated so the in-memory
 * store a test drives cannot accept an identity the real one would reject.
 */
internal fun credentialsOrNull(apiId: Int, apiHash: String?): TelegramCredentials? =
    if (apiId > 0 && !apiHash.isNullOrBlank()) TelegramCredentials(apiId, apiHash) else null

interface TelegramSettings {
    suspend fun read(): TelegramCredentials?
    suspend fun write(apiId: Int, apiHash: String)
    suspend fun clear()
}

/** In-memory implementation for tests; nothing here ever touches disk. */
class InMemoryTelegramSettings : TelegramSettings {

    @Volatile
    private var apiId: Int = 0

    @Volatile
    private var apiHash: String? = null

    override suspend fun read(): TelegramCredentials? = credentialsOrNull(apiId, apiHash)

    override suspend fun write(apiId: Int, apiHash: String) {
        this.apiId = apiId
        this.apiHash = apiHash
    }

    override suspend fun clear() {
        apiId = 0
        apiHash = null
    }
}

/**
 * Persists the identity in its own keystore-backed preferences file,
 * under the same encryption the package key gets — the api hash is a
 * secret and is protected as one.
 *
 * The store is opened on first use, not in the constructor. Opening it
 * talks to the Android keystore, which throws after a restore onto another
 * device or when the key behind it has been invalidated; thrown from a
 * constructor that dependency injection runs, that is an unconditional
 * crash on every launch, with no screen reached to say so or to offer
 * starting over.
 */
class EncryptedTelegramSettings(private val context: Context) : TelegramSettings {

    private val preferences by lazy { encryptedPreferences(context, PREFS_FILE_NAME) }

    override suspend fun read(): TelegramCredentials? =
        credentialsOrNull(preferences.getInt(KEY_API_ID, 0), preferences.getString(KEY_API_HASH, null))

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
