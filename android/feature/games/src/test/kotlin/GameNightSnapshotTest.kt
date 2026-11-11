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
import uniffi.mediagram_core.CatalogFacts
import uniffi.mediagram_core.NightTitlesConfig
import uniffi.mediagram_core.Profile
import kotlin.test.assertEquals
import kotlin.test.assertFalse

/**
 * The night kept on disk and in memory: served only while everything it was built from is unchanged
 * (who, their kids and section rules, the kids marks, the catalog), built afresh otherwise, and never
 * the old night for a kids profile whose rules have tightened, not even when the new build fails.
 */
@RunWith(RobolectricTestRunner::class)
class GameNightSnapshotTest {
    @get:Rule val folder = TemporaryFolder()

    private val f by lazy { NightFixture(folder.root) }
    private val everything by lazy { "[${f.alien},${f.bambi}]" }

    @Before
    fun spieltischHoldsThePermission() = shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(GameNightProvider.PERMISSION)

    private fun builds() = f.core.nightTitlesAsked.size

    @Test
    fun theNightAndItsImagesComeFromTheSnapshotWithoutARebuild() {
        assertEquals(0 to everything, f.night())
        // Each call below is a fresh process, with nothing but the file to go on.
        assertEquals(0 to everything, f.night())
        assertEquals("alien", f.read("image/A/poster"))
        f.refused("image/Z/poster")
        assertEquals(1, builds(), "built once, then read")
    }

    @Test
    fun aSnapshotForAnotherProfileIsRebuiltForTheActiveOne() {
        f.night()
        // Another grown-up, who may hide sections the first one sees: same lock, still not the same night.
        f.core.profiles += Profile("b", "Bea")
        f.core.chosen = "b"
        f.night()
        assertEquals(2, builds())

        f.core.chosen = "k"
        assertEquals(1 to "[${f.bambi}]", f.night())
        f.refused("image/A/poster")
    }

    @Test
    fun aGrownUpsSnapshotNeverUnlocksAProfileThatBecameAKid() {
        val process = f.export()
        assertEquals(0 to everything, f.night(f.provider(process)))
        // Limited to 12, a kid's limit counts as a grown-up's: only the kids flag tells them apart.
        f.core.profiles = listOf(f.grownUp.copy(kids = true, kidsAge = 12u), f.kid)

        assertEquals(1 to "[${f.bambi}]", f.night(f.provider(process)), "the same process reads the profile as it is now")
        f.refused("image/A/poster", f.provider(process))
        assertEquals(1 to "[${f.bambi}]", f.night())
    }

    /**
     * The older kid's night is built and kept, its rules are tightened, and the build that should
     * follow fails: neither the process that holds the old night nor a fresh one serves it.
     */
    private fun afterTightening(
        tighten: () -> Unit,
        check: (NightTitlesConfig) -> Unit,
    ) {
        f.core.sets = listOf(movie("f0", "0"), movie("f12", "12"), movie("marked", null), movie("ghibli", "0"))
        runBlocking { f.core.setKids("marked", 12u) }
        f.olderKid.tightened { copy(hiddenSections = listOf("anime")) }
        f.core.chosen = "t"
        val process = f.export()
        assertEquals(1 to "[${f.bambi}]", f.night(f.provider(process)))
        tighten()
        val answer = f.core.nightTitles
        f.core.nightTitles = { error("the catalog could not be read") }

        assertEquals(1 to "[]", f.night(f.provider(process)), "the same process: locked, not the night it holds")
        assertEquals(1 to "[]", f.night(), "a fresh process: locked, not the night on disk")
        f.core.nightTitles = answer
        f.night(f.provider(process))
        check(f.core.nightTitlesAsked.last())
    }

    private fun Profile.tightened(change: Profile.() -> Profile) {
        f.core.profiles = f.core.profiles.map { if (it.id == id) it.change() else it }
    }

    @Test
    fun aLowerKidsLimitIsNeverServedTheOldNight() =
        afterTightening({ f.olderKid.tightened { copy(kidsAge = 6u) } }) { assertFalse("f12" in it.kidsSetIds) }

    @Test
    fun aHiddenSectionIsNeverServedTheOldNight() =
        afterTightening({ f.olderKid.tightened { copy(hiddenSections = listOf("anime", "movies")) } }) { assertEquals(emptyList(), it.visibleSetIds) }

    @Test
    fun aRemovedKidsMarkIsNeverServedTheOldNight() =
        afterTightening({ runBlocking { f.core.setKids("marked", null) } }) { assertFalse("marked" in it.kidsSetIds) }

    @Test
    fun aStricterRatingInANewCatalogIsNeverServedTheOldNight() =
        afterTightening({
            f.core.sets = listOf(movie("f0", "0"), movie("f12", "16"), movie("marked", null), movie("ghibli", "0"))
            f.core.publishedAt = listOf(2L)
        }) { assertFalse("f12" in it.kidsSetIds) }

    @Test
    fun aFetchedDescriptionThatMovesATitleIntoAHiddenSectionIsNeverServedTheOldNight() =
        afterTightening({
            // The same index: only what this device fetched about the film says it is anime now.
            f.core.sets = listOf(movie("f0", "0"), movie("f12", "12"), movie("marked", null), movie("ghibli", "0", anime = true))
            f.core.catalogFactsAnswer = { CatalogFacts("channel", 0uL, 0uL, 0u, null, described = 7uL) }
        }) { assertFalse("ghibli" in it.visibleSetIds) }

    @Test
    fun aSnapshotFromAnotherVersionOfThisAppIsNotServed() {
        assertEquals(0 to everything, f.night(f.provider(f.export(version = 1))))
        assertEquals(0 to everything, f.night(f.provider(f.export(version = 1))))
        assertEquals(1, builds())

        // The update may carry a rule fix: its first night is built by it, not read from before.
        assertEquals(0 to everything, f.night(f.provider(f.export(version = 2))))
        assertEquals(2, builds())
    }

    @Test
    fun aSnapshotThatCannotBeReadIsRebuilt() {
        for (corrupt in listOf("not a night", "{\"fingerprint\":\"x\",\"kids_locked\":false}\n[{\"key\":", "{\"fingerprint\":\"x\"}\n[]")) {
            f.snapshots.mkdirs()
            NightSnapshotStore(f.snapshots, 1).file.writeText(corrupt)
            val before = builds()

            assertEquals(0 to everything, f.night(), corrupt)
            assertEquals(before + 1, builds(), corrupt)
        }
    }

    @Test
    fun aSnapshotThatCannotBeReadOrRebuiltIsLocked() {
        f.snapshots.mkdirs()
        NightSnapshotStore(f.snapshots, 1).file.writeText("not a night")
        f.core.nightTitles = { error("the catalog could not be read") }

        assertEquals(1 to "[]", f.night())
        f.refused("image/B/poster")
    }
}
