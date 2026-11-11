package games

import kotlinx.coroutines.awaitCancellation
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
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * When a night is built: never for watch state that does not decide it, once for asks that come
 * together, never kept when what it was built from moved meanwhile, and never for an image check.
 */
@RunWith(RobolectricTestRunner::class)
class GameNightBuildTest {
    @get:Rule val folder = TemporaryFolder()

    private val f by lazy { NightFixture(folder.root) }

    @Before
    fun spieltischHoldsThePermission() = shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(GameNightProvider.PERMISSION)

    private fun builds() = f.core.nightTitlesAsked.size

    private fun fingerprint() = runBlocking { checkNotNull(f.core.nightInputs(appVersion = 1)).fingerprint }

    @Test
    fun progressAndOtherWatchStateNeverRebuild() {
        val process = f.export()
        f.night(f.provider(process))
        val before = fingerprint()

        runBlocking {
            f.core.setProgress("a", "f0", 600.0, 5400.0, "2026-11-11")
            f.core.setWatched("a", "f16", true)
            f.core.setWatchlisted("a", "f0", true)
        }

        assertEquals(before, fingerprint())
        f.night(f.provider(process))
        f.night()
        assertEquals(1, builds())
    }

    @Test
    fun aNightWhoseInputsMoveWhileItIsBuiltIsNotKept() {
        val answer = f.core.nightTitles
        // The kid is chosen while the grown-up's night is still being built.
        f.core.nightTitles = { config -> answer(config).also { f.core.chosen = "k" } }

        assertEquals(1 to "[]", f.night(), "the grown-up's night is not handed out once the kid is chosen")
        assertFalse(NightSnapshotStore(f.snapshots, 1).file.exists(), "nor kept")
        f.core.nightTitles = answer
        assertEquals(1 to "[${f.bambi}]", f.night())
    }

    @Test
    fun asksThatComeTogetherBuildOnce() {
        val building = CountDownLatch(1)
        val release = CountDownLatch(1)
        val answer = f.core.nightTitles
        f.core.nightTitles = { config ->
            building.countDown()
            release.await(5, TimeUnit.SECONDS)
            answer(config)
        }
        val process = f.export()
        val (first, second) = f.provider(process) to f.provider(process)
        val answers = arrayOfNulls<Pair<Int, String>>(2)

        val one = thread(isDaemon = true) { answers[0] = f.night(first) }
        assertTrue(building.await(5, TimeUnit.SECONDS))
        val two = thread(isDaemon = true) { answers[1] = f.night(second) }
        // Long enough for a second ask that does not wait to start a build of its own.
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(500)
        while (builds() < 2 && System.nanoTime() < deadline) Thread.sleep(10)
        release.countDown()
        one.join()
        two.join()

        assertEquals(1, builds())
        val everything = 0 to "[${f.alien},${f.bambi}]"
        assertEquals(listOf(everything, everything), answers.toList(), "both answered, with the one night")
    }

    @Test
    fun imagesAreCheckedAgainstTheServedNightWithoutABuild() {
        val process = f.export()
        f.night(f.provider(process))
        // A running night keeps the titles it started with, as a night hosted here does.
        f.core.chosen = "k"

        assertEquals("alien", f.read("image/A/poster", f.provider(process)))
        assertEquals("bambi", f.read("image/B/poster", f.provider(process)))
        assertEquals(1, builds())
        // A process that has served no night answers one first: the kid's, which has no Alien.
        f.refused("image/A/poster")
    }

    @Test
    fun aCatalogWhoseVersionCannotBeReadAnswersLocked() {
        f.core.catalogFactsAnswer = { CatalogFacts("channel", 3uL, 0uL, 0u, null) }

        assertEquals(1 to "[]", f.night())
        assertEquals(0, builds(), "nobody can vouch for a night built from it")
    }

    @Test
    fun anAskThatNeverFinishesLetsTheNextOneInAfterTheLimit() {
        val process = f.export().apply { lockedWorkLimit = 300.milliseconds }
        val answer = f.core.catalogFactsAnswer
        val stuck = CountDownLatch(1)
        f.core.catalogFactsAnswer = {
            stuck.countDown()
            awaitCancellation()
        }
        val (first, second) = f.provider(process) to f.provider(process)
        val answers = arrayOfNulls<Pair<Int, String>>(2)

        val one = thread(isDaemon = true) { answers[0] = f.night(first) }
        assertTrue(stuck.await(5, TimeUnit.SECONDS))
        f.core.catalogFactsAnswer = answer
        val two = thread(isDaemon = true) { answers[1] = f.night(second) }
        one.join(5_000)
        two.join(5_000)

        assertEquals(listOf(1 to "[]", 0 to "[${f.alien},${f.bambi}]"), answers.toList(), "the stuck ask locked, the next one answered")
    }

    @Test
    fun aSignOutDuringTheAskNeitherHoldsItNorWaitsForTheNextSignIn() {
        val process = f.export().apply { lockedWorkLimit = 2.seconds }
        val answer = f.core.catalogFactsAnswer
        f.core.catalogFactsAnswer = {
            // Signed out once the inputs are read: the night is built from the core in hand.
            f.coreProvider.set(null)
            answer()
        }

        assertEquals(0 to "[${f.alien},${f.bambi}]", f.night(f.provider(process)))
    }
}
