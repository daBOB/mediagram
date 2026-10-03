package stats

import data.CoreProvider
import data.DefaultWatchStateRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testing.FakeCore
import testing.ResolvedCoreProvider
import uniffi.mediagram_core.Achievements
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.EarnedAchievement
import uniffi.mediagram_core.Profile
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AchievementDotViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val core =
        FakeCore().apply {
            profiles = listOf(Profile("a", "Ada"), Profile("b", "Ben"))
            chosen = "a"
        }
    private val watch = DefaultWatchStateRepository(ResolvedCoreProvider(core), Dispatchers.Unconfined)
    private val seen = InMemoryAchievementsSeen()

    private fun earned(vararg ids: String) = Achievements(earned = ids.map { EarnedAchievement(id = it, earnedAt = 1L) }, next = emptyList())

    /** The dot as the rail holds it: subscribed, so the reads run. */
    private fun TestScope.dot(provider: CoreProvider = ResolvedCoreProvider(core)): StateFlow<Boolean> {
        val model = AchievementDotViewModel(provider, watch, seen).apply { now = { Now } }
        backgroundScope.launch { model.newAchievement.collect {} }
        advanceUntilIdle()
        return model.newAchievement
    }

    @Test
    fun anAchievementThisDeviceHasNotShownLightsTheDot() =
        runTest {
            core.achievementsByProfile = mapOf("a" to earned("films-1"))
            watch.reload()
            assertTrue(dot().value)
        }

    @Test
    fun whatThisDeviceHasShownLeavesItOut() =
        runTest {
            core.achievementsByProfile = mapOf("a" to earned("films-1"))
            seen.markSeen("a", setOf("films-1"))
            watch.reload()
            assertFalse(dot().value)
        }

    @Test
    fun anAchievementASyncBroughtFromAnotherDeviceLightsItOnTheNextRead() =
        runTest {
            watch.reload()
            val dot = dot()
            assertFalse(dot.value)

            // The television finished a film; the round that pulled its rows reloads the snapshot.
            core.achievementsByProfile = mapOf("a" to earned("films-1"))
            core.setWatched("a", "f1", true)
            watch.reload()
            advanceTimeBy(DOT_SETTLE_MS - 1)
            assertFalse(dot.value, "not yet: changes settle first")

            advanceTimeBy(2)
            assertTrue(dot.value)
        }

    @Test
    fun positionsSavedWhileATitlePlaysAreNotReadOneByOne() =
        runTest {
            watch.reload()
            dot()
            val before = core.achievementsAsked.size

            repeat(6) { tick ->
                watch.setProgress("f1", tick * 10.0, 3_600.0)
                advanceTimeBy(10_000)
            }
            assertEquals(before, core.achievementsAsked.size, "a save every ten seconds never lets it settle")

            advanceTimeBy(DOT_SETTLE_MS)
            assertEquals(before + 1, core.achievementsAsked.size, "one read once the title stops")
        }

    @Test
    fun theStatsPageShowingTheAchievementPutsItOut() =
        runTest {
            core.achievementsByProfile = mapOf("a" to earned("films-1"))
            watch.reload()
            val dot = dot()
            assertTrue(dot.value)

            seen.markSeen("a", setOf("films-1"))
            advanceUntilIdle()

            assertFalse(dot.value)
        }

    @Test
    fun aProfileSwitchReadsTheNewProfilesOwn() =
        runTest {
            core.achievementsByProfile = mapOf("a" to earned("films-1"), "b" to earned("films-1"))
            seen.markSeen("b", setOf("films-1"))
            watch.reload()
            val dot = dot()
            assertTrue(dot.value)

            watch.chooseProfile("b")
            advanceUntilIdle()

            assertFalse(dot.value)
        }

    @Test
    fun aSwitchPutsTheLastProfilesDotOutBeforeTheNewReadLands() =
        runTest {
            core.achievementsByProfile = mapOf("a" to earned("films-1"), "b" to earned("streak-7"))
            val bHeld = CompletableDeferred<Unit>()
            val slow =
                object : CoreInterface by core {
                    override suspend fun achievements(
                        profileId: String,
                        today: String,
                        utcOffsetMinutes: Int,
                    ): Achievements {
                        if (profileId == "b") bHeld.await()
                        return core.achievements(profileId, today, utcOffsetMinutes)
                    }
                }
            watch.reload()
            val dot = dot(ResolvedCoreProvider(slow))
            assertTrue(dot.value)

            watch.chooseProfile("b")
            advanceUntilIdle()
            assertFalse(dot.value, "Ada's dot is not Ben's, even while Ben's read is out")

            bHeld.complete(Unit)
            advanceUntilIdle()
            assertTrue(dot.value, "Ben's own unseen achievement")
        }

    @Test
    fun theReadAsksForThisDevicesDayAndOffset() =
        runTest {
            watch.reload()
            dot()
            assertEquals(Triple("a", "2026-09-26", 120), core.achievementsAsked.last())
        }
}
