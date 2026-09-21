package settings

import android.content.Context

/**
 * What counts as a stored API key, for every store alike.
 *
 * A blank key is what an absent string looks like coming back, and a core
 * built on one would fail at TMDB rather than here. Shared rather than
 * repeated so the in-memory store a test drives cannot accept a key the
 * real one would reject.
 */
internal fun keyOrNull(key: String?): String? = if (!key.isNullOrBlank()) key else null

interface TmdbSettings {
    suspend fun read(): String?
    suspend fun write(key: String)
    suspend fun clear()
}

/** In-memory implementation for tests; nothing here ever touches disk. */
class InMemoryTmdbSettings : TmdbSettings {

    @Volatile
    private var stored: String? = null

    override suspend fun read(): String? = keyOrNull(stored)

    override suspend fun write(key: String) {
        stored = key
    }

    override suspend fun clear() {
        stored = null
    }
}

/**
 * Persists the key in its own keystore-backed preferences file, under the
 * same encryption the package key gets — the TMDB key is a secret and is
 * protected as one.
 *
 * The store is opened on first use, not in the constructor. Opening it
 * talks to the Android keystore, which throws after a restore onto another
 * device or when the key behind it has been invalidated; thrown from a
 * constructor that dependency injection runs, that is an unconditional
 * crash on every launch, with no screen reached to say so or to offer
 * starting over.
 */
class EncryptedTmdbSettings(private val context: Context) : TmdbSettings {

    private val preferences by lazy { encryptedPreferences(context, PREFS_FILE_NAME) }

    override suspend fun read(): String? =
        keyOrNull(preferences.getString(KEY_TMDB, null))

    override suspend fun write(key: String) {
        preferences.edit().putString(KEY_TMDB, key).apply()
    }

    override suspend fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "tmdb_settings"
        const val KEY_TMDB = "tmdb_key"
    }
}
