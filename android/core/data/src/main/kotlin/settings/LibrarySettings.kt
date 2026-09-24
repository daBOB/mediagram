package settings

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext

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

    /** The current choice and successful changes to it, including clearing it. */
    fun selections(): Flow<String?>

    suspend fun write(handle: String)

    suspend fun clear()
}

/** In-memory implementation for tests; nothing here ever touches disk. */
class InMemoryLibrarySettings : LibrarySettings {
    private val stored = MutableStateFlow<String?>(null)

    override suspend fun read(): String? = stored.value

    override fun selections(): Flow<String?> = stored.asStateFlow()

    override suspend fun write(handle: String) {
        stored.value = handle
    }

    override suspend fun clear() {
        stored.value = null
    }
}

class EncryptedLibrarySettings(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : LibrarySettings {
    // Opened on first use, not in the constructor: see EncryptedTelegramSettings
    // for why a keystore failure must not happen where nothing can catch it.
    private val preferences by lazy { encryptedPreferences(context, PREFS_FILE_NAME) }
    private val revision = MutableStateFlow(0L)

    override suspend fun read(): String? = withContext(dispatcher) { preferences.getString(KEY_HANDLE, null) }

    // Read lazily for each collector and after a successful mutation. A
    // keystore refusal reaches the collector's recovery boundary, and a
    // retry opens it again rather than keeping a failed initial snapshot.
    override fun selections(): Flow<String?> = revision.map { read() }.distinctUntilChanged()

    override suspend fun write(handle: String) {
        preferences.edit().putString(KEY_HANDLE, handle).apply()
        revision.update { it + 1 }
    }

    override suspend fun clear() {
        preferences.edit().clear().apply()
        revision.update { it + 1 }
    }

    private companion object {
        const val PREFS_FILE_NAME = "library_settings"
        const val KEY_HANDLE = "library_handle"
    }
}
