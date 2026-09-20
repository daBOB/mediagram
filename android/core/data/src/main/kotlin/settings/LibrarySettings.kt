package settings

import android.content.Context

/**
 * Which library this device reads, as the handle the core minted for it.
 *
 * A handle is a random name, meaningless outside the core that issued it,
 * so this is not a secret the way the package key was. It is stored in the
 * same encrypted preferences as everything else the setup flow keeps
 * because it belongs to the same device identity: it names a channel this
 * account is in, and it is cleared by the same "start over" that takes the
 * session and the catalog. Putting it somewhere weaker would mean two
 * places to remember to clear.
 */
interface LibrarySettings {
    /** The chosen handle, or `null` when no library has been picked yet. */
    suspend fun read(): String?
    suspend fun write(handle: String)
    suspend fun clear()
}

/** In-memory implementation for tests; nothing here ever touches disk. */
class InMemoryLibrarySettings : LibrarySettings {

    @Volatile
    private var stored: String? = null

    override suspend fun read(): String? = stored

    override suspend fun write(handle: String) {
        stored = handle
    }

    override suspend fun clear() {
        stored = null
    }
}

class EncryptedLibrarySettings(private val context: Context) : LibrarySettings {

    // Opened on first use, not in the constructor: see EncryptedTelegramSettings
    // for why a keystore failure must not happen where nothing can catch it.
    private val preferences by lazy { encryptedPreferences(context, PREFS_FILE_NAME) }

    override suspend fun read(): String? = preferences.getString(KEY_HANDLE, null)

    override suspend fun write(handle: String) {
        preferences.edit().putString(KEY_HANDLE, handle).apply()
    }

    override suspend fun clear() {
        preferences.edit().clear().apply()
    }

    private companion object {
        const val PREFS_FILE_NAME = "library_settings"
        const val KEY_HANDLE = "library_handle"
    }
}
