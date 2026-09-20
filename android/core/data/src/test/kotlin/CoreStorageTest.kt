package data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.test.Test
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
    fun theSessionFileIsDeletedRatherThanForgotten() = runTest {
        val session = File(dataDir.root, "session.key").apply { writeText("an auth key") }

        storage().clear()

        assertFalse(session.exists())
    }

    @Test
    fun theCatalogGoesWithTheKeyThatDecryptedIt() = runTest {
        val catalog = File(dataDir.root, "catalog/current").apply { mkdirs() }
        File(catalog, "library.db").writeText("rows")

        storage().clear()

        assertFalse(File(dataDir.root, "catalog").exists())
    }

    @Test
    fun clearingADirectoryThatHoldsNothingYetIsNotAFailure() = runTest {
        storage().clear()

        assertFalse(File(dataDir.root, "session.key").exists())
    }
}
