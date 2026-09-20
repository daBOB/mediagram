package data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * The state the native core keeps for itself, under the data directory it
 * was constructed with.
 *
 * Forgetting a credential in encrypted preferences signs nobody out: the
 * auth key is a file the core wrote, and it keeps working until the file is
 * gone. The core's surface has no call that removes it, so the names are
 * matched here — an external contract, the same way a file format is.
 */
interface CoreStorage {
    /**
     * Deletes the persisted sign-in and the decrypted catalog together.
     *
     * Together on purpose: a catalog is the contents of one library,
     * decrypted with one key. Keeping it after the key that produced it has
     * been discarded would show the next person to set this device up a
     * library they cannot refresh and were never given.
     */
    suspend fun clear()
}

/** In-memory implementation for tests; nothing here ever touches disk. */
class InMemoryCoreStorage : CoreStorage {

    @Volatile
    var cleared: Boolean = false
        private set

    override suspend fun clear() {
        cleared = true
    }
}

class FileCoreStorage(
    private val dataDir: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoreStorage {

    override suspend fun clear() {
        withContext(dispatcher) {
            File(dataDir, SESSION_FILE).delete()
            File(dataDir, CATALOG_DIR).deleteRecursively()
        }
    }

    private companion object {
        const val SESSION_FILE = "session.key"
        const val CATALOG_DIR = "catalog"
    }
}
