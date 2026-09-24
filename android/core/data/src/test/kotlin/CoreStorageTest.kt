package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/**
 * Starting over has to remove files, not just forget references to them.
 * An auth key that is still on disk is still a live session: the next core
 * built over the same directory reads it back and reports itself signed in,
 * which is exactly the stuck state this path exists to escape.
 */
class CoreStorageTest {
    @get:Rule
    val dataDir = TemporaryFolder()

    private fun storage() = FileCoreStorage(dataDir.root, Dispatchers.Unconfined)

    @Test
    fun theSessionFileIsDeletedRatherThanForgotten() =
        runTest {
            val session = File(dataDir.root, "session.key").apply { writeText("an auth key") }

            storage().clear()

            assertFalse(session.exists())
        }

    @Test
    fun theCatalogGoesWithTheKeyThatDecryptedIt() =
        runTest {
            val catalog = File(dataDir.root, "catalog/current").apply { mkdirs() }
            File(catalog, "library.db").writeText("rows")

            storage().clear()

            assertFalse(File(dataDir.root, "catalog").exists())
        }

    @Test
    fun clearingADirectoryThatHoldsNothingYetIsNotAFailure() =
        runTest {
            storage().clear()

            assertFalse(File(dataDir.root, "session.key").exists())
        }

    /**
     * A start-over promises a clean device, and this device's own watch
     * state — its viewers, its positions — is exactly the kind of thing
     * left behind that would surprise whoever sets it up next.
     */
    @Test
    fun theWatchStateGoesWithItsWalAndShmSidecars() =
        runTest {
            File(dataDir.root, "state.db").writeText("rows")
            File(dataDir.root, "state.db-wal").writeText("uncheckpointed rows")
            File(dataDir.root, "state.db-shm").writeText("shared index")

            storage().clear()

            assertFalse(File(dataDir.root, "state.db").exists())
            assertFalse(File(dataDir.root, "state.db-wal").exists())
            assertFalse(File(dataDir.root, "state.db-shm").exists())
        }

    @Test
    fun noWatchStateOnDiskYetIsNotAFailure() =
        runTest {
            storage().clear()

            assertFalse(File(dataDir.root, "state.db").exists())
        }

    /**
     * `delete()` returns a Boolean and a discarded one is a silent
     * failure: the app would go back to the first step with a live auth key
     * still on disk, and the next identity typed in would inherit the
     * previous account's session. A directory with something in it is the
     * cheapest way to make the call fail for real.
     */
    @Test
    fun aSessionThatCannotBeDeletedIsReportedRatherThanSwallowed() =
        runTest {
            val blocked = File(dataDir.root, "session.key").apply { mkdirs() }
            File(blocked, "occupied").writeText("in the way")

            assertFailsWith<IOException> { storage().clear() }
        }
}
