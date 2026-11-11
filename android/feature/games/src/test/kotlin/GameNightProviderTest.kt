package games

import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import testing.FakeCoreProvider
import uniffi.mediagram_core.NightTitlesConfig
import java.io.FileNotFoundException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/**
 * What Spieltisch reads from this app: the active profile's night under the rules an in-app night
 * follows, its titles' files by key and nothing else, and nothing at all without the permission.
 */
@RunWith(RobolectricTestRunner::class)
class GameNightProviderTest {
    @get:Rule val folder = TemporaryFolder()

    private val app = RuntimeEnvironment.getApplication()
    private val f by lazy { NightFixture(folder.root) }

    @Before
    fun spieltischHoldsThePermission() = shadowOf(app).grantPermissions(GameNightProvider.PERMISSION)

    @Test
    fun aGrownUpsNightIsUnlockedWithEveryTitleItSees() {
        assertEquals(0 to "[${f.alien},${f.bambi}]", f.night())
        assertEquals(NightTitlesConfig(listOf("f0"), listOf("f0", "f16"), kidsLocked = false), f.core.nightTitlesAsked.single())
    }

    @Test
    fun aKidsProfilesNightIsLockedToItsKidSafeTitles() {
        f.core.chosen = "k"

        assertEquals(1 to "[${f.bambi}]", f.night())
        assertEquals(NightTitlesConfig(listOf("f0"), listOf("f0", "f16"), kidsLocked = true), f.core.nightTitlesAsked.single())
    }

    @Test
    fun noProfileMeansLockedAndNoTitles() {
        f.core.chosen = null

        assertEquals(1 to "[]", f.night())
        assertEquals(emptyList(), f.core.nightTitlesAsked, "the library is never read for nobody")
    }

    @Test
    fun noCoreYetOrACoreThatFailsMeansLockedAndNoTitles() {
        f.coreProvider = FakeCoreProvider(null)
        assertEquals(1 to "[]", f.night())

        f.coreProvider = FakeCoreProvider(f.core)
        f.core.nightTitles = { error("the catalog could not be read") }
        assertEquals(1 to "[]", f.night())
    }

    @Test
    fun aTitlesFilesAreServedByKey() {
        runBlocking { f.tmdb.write("tmdb") }
        f.core.textlessPosters = mapOf("A" to f.file("alien-textless.jpg", "alien textless").path)

        assertEquals("alien", f.read("image/A/poster"))
        assertEquals("alien still", f.read("image/A/still"))
        assertEquals("alien textless", f.read("textless/A"))
        assertEquals(listOf<Pair<String, String?>>("A" to "tmdb"), f.core.textlessAsked, "the core fetches it with this app's TMDB key")
    }

    @Test
    fun anUnknownKeyOrImageIsNotFound() {
        listOf("image/Z/poster", "textless/Z", "image/B/still", "image/A/title", "image/A", "night", "other").forEach { f.refused(it) }
    }

    @Test
    fun aTitleOutsideTheProfilesNightIsNotFoundThoughItsFileIsThere() {
        f.core.chosen = "k"

        f.refused("image/A/poster")
        f.refused("textless/A")
        assertEquals("bambi", f.read("image/B/poster"))
        assertEquals(emptyList(), f.core.textlessAsked)
    }

    @Test
    fun nothingIsWritable() {
        val provider = f.provider()
        listOf("w", "rw", "wt").forEach { mode -> assertFailsWith<FileNotFoundException>(mode) { provider.openFile(f.uri("image/A/poster"), mode) } }
        assertFailsWith<UnsupportedOperationException> { provider.insert(f.uri("night"), null) }
        assertFailsWith<UnsupportedOperationException> { provider.delete(f.uri("night"), null, null) }
    }

    @Test
    fun aCallerWithoutThePermissionIsRefused() {
        shadowOf(app).denyPermissions(GameNightProvider.PERMISSION)
        val provider = f.provider()

        assertFailsWith<SecurityException> { provider.query(f.uri("night"), null, null, null, null) }
        assertFailsWith<SecurityException> { provider.openFile(f.uri("image/B/poster"), "r") }
        assertEquals(emptyList(), f.core.nightTitlesAsked)
    }
}
