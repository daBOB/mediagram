package homerows

import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import testing.FakeCore
import testing.FakeCoreProvider
import testing.setSummary
import uniffi.mediagram_core.Profile
import java.io.File
import java.io.FileNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * What the TV home screen (our saTV) reads from this app: the chosen person, their Continue and
 * My List in the contract's columns, the pictures of those titles only, and nothing writable.
 */
@RunWith(RobolectricTestRunner::class)
class HomeRowsProviderTest {
    @get:Rule val folder = TemporaryFolder()

    private val app = RuntimeEnvironment.getApplication()
    private lateinit var core: FakeCore
    private lateinit var coreProvider: FakeCoreProvider

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(HomeRowsProvider.PERMISSION)
        val poster = File(folder.root, "dark.jpg").apply { writeText("dark poster") }
        core =
            FakeCore().apply {
                profiles = listOf(Profile("a", "Ada"))
                chosen = "a"
                sets =
                    listOf(
                        setSummary(setId = "m1", title = "Old", duration = 3_600),
                        setSummary(setId = "m2", title = "New", duration = 3_600, posterPath = poster.path),
                        setSummary(setId = "m3", title = "Later", year = 2021),
                        setSummary(setId = "m4", title = "Unlisted", posterPath = poster.path),
                    )
            }
        kotlinx.coroutines.runBlocking {
            core.setProgress("a", "m1", 1_800.0, 3_600.0, "2026-11-27")
            core.setProgress("a", "m2", 600.0, 3_600.0, "2026-11-27")
            core.setWatchlisted("a", "m3", true)
        }
        coreProvider = FakeCoreProvider(core)
    }

    @Test
    fun personRowHasIdNameKids() {
        rows("person").use {
            it.moveToFirst()
            assertEquals("a", it.string(HomeRowsProvider.ID))
            assertEquals("Ada", it.string(HomeRowsProvider.NAME))
            assertEquals(0, it.getInt(it.getColumnIndexOrThrow(HomeRowsProvider.KIDS)))
        }
    }

    @Test
    fun noPersonRowWhenNobodyIsChosen() {
        core.chosen = null
        rows("person").use { assertEquals(0, it.count) }
        rows("continue").use { assertEquals(0, it.count) }
    }

    @Test
    fun continueColumnsAndOrderMatchTheContract() {
        rows("continue").use {
            assertEquals(
                listOf("set_id", "kind", "title", "show", "season", "episode", "position_secs", "duration_secs", "updated_at"),
                it.columnNames.toList(),
            )
            val titles = generateSequence { if (it.moveToNext()) it.string("title") to it.getDouble(it.getColumnIndexOrThrow("position_secs")) else null }.toList()
            assertEquals(setOf("Old" to 1_800.0, "New" to 600.0), titles.toSet())
        }
        rows("mylist").use {
            assertEquals(listOf("set_id", "kind", "title", "show", "year"), it.columnNames.toList())
            it.moveToFirst()
            assertEquals("Later", it.string("title"))
            assertEquals(2021, it.getInt(it.getColumnIndexOrThrow("year")))
        }
    }

    @Test
    fun imageOfATitleOnTheRowsIsServed() {
        val text = ParcelFileDescriptor.AutoCloseInputStream(provider().openFile(uri("image/m2/poster"), "r")).bufferedReader().readText()
        assertEquals("dark poster", text)
    }

    @Test
    fun imageOfATitleNotOnTheRowsIsRefused() {
        assertFailsWith<FileNotFoundException> { provider().openFile(uri("image/m4/poster"), "r") }
    }

    @Test
    fun writesAreRefused() {
        assertFailsWith<UnsupportedOperationException> { provider().insert(uri("continue"), null) }
    }

    @Test
    fun aFailingCoreAnswersAnEmptyCursor() {
        coreProvider.beforeCore = { error("no core") }
        rows("person").use { assertEquals(0, it.count) }
        rows("continue").use { assertEquals(0, it.count) }
    }

    @Test
    fun withoutThePermissionNothingIsRead() {
        shadowOf(app).denyPermissions(HomeRowsProvider.PERMISSION)
        assertFailsWith<SecurityException> { provider().query(uri("person"), null, null, null, null) }
    }

    private fun provider() =
        Robolectric.buildContentProvider(HomeRowsProvider::class.java).create(HomeRowsProvider.AUTHORITY).get().also {
            it.source = HomeRowsSource(coreProvider)
        }

    private fun rows(path: String): Cursor = checkNotNull(provider().query(uri(path), null, null, null, null))

    private fun uri(path: String): Uri = Uri.parse("content://${HomeRowsProvider.AUTHORITY}/$path")

    private fun Cursor.string(column: String): String? = getString(getColumnIndexOrThrow(column))
}
