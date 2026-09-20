package data

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

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
     * Deletes the persisted sign-in, the decrypted catalog and the names
     * this device minted for the channels it could read, all together.
     *
     * Together on purpose: a catalog is the contents of one library, read
     * by one account. Keeping it after that account has been signed out
     * would show the next person to set this device up a library they were
     * never given, and keeping the names would leave a list of that
     * account's channels behind on a device it no longer has a session on.
     */
    suspend fun clear()
}

/**
 * In-memory implementation for tests; nothing here ever touches disk.
 * [failWith] stands in for a delete that does not work — a read-only file,
 * a directory a restore left owned by nobody — which is the case the reset
 * path has to survive rather than half-finish.
 */
class InMemoryCoreStorage(private val failWith: Exception? = null) : CoreStorage {

    @Volatile
    var cleared: Boolean = false
        private set

    override suspend fun clear() {
        failWith?.let { throw it }
        cleared = true
    }
}

class FileCoreStorage(
    private val dataDir: File,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : CoreStorage {

    /**
     * Both deletes report whether they worked, and both answers are
     * checked. A delete that silently failed would leave the app back at
     * the first step with a live auth key still on disk, and the next
     * identity typed in would inherit the previous account's session —
     * which is the one outcome this whole path exists to prevent.
     */
    override suspend fun clear() {
        withContext(dispatcher) {
            val session = File(dataDir, SESSION_FILE)
            if (session.exists() && !session.delete()) {
                throw IOException("the stored sign-in could not be deleted")
            }
            val catalog = File(dataDir, CATALOG_DIR)
            if (catalog.exists() && !catalog.deleteRecursively()) {
                throw IOException("the stored library could not be deleted")
            }
            val libraries = File(dataDir, LIBRARIES_FILE)
            if (libraries.exists() && !libraries.delete()) {
                throw IOException("the stored list of libraries could not be deleted")
            }
        }
    }

    private companion object {
        const val SESSION_FILE = "session.key"
        const val CATALOG_DIR = "catalog"
        const val LIBRARIES_FILE = "libraries.json"
    }
}
