package stats

import data.DefaultWatchStateRepository
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import testing.FakeCore
import testing.FakeCoreProvider
import testing.MainDispatcherRule
import uniffi.mediagram_core.Achievements
import uniffi.mediagram_core.CoreInterface
import uniffi.mediagram_core.EarnedAchievement
import uniffi.mediagram_core.NextAchievement
import uniffi.mediagram_core.Profile
import uniffi.mediagram_core.StatsSummary
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A core whose stats are set per profile, whose read for "a" can be held open, and which records every read. */
private class StatsCore(
    raw: FakeCore,
) : CoreInterface by raw {
    val answers = mutableMapOf<String, StatsSummary>()
    val asked = mutableListOf<Pair<String, String>>()
    var holdA: CompletableDeferred<Unit>? = null

    override suspend fun stats(
        profileId: String,
        today: String,
    ): StatsSummary {
        asked += profileId to today
        if (profileId == "a") holdA?.await()
        return answers.getValue(profileId)
    }
}

class StatsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    private val raw =
        FakeCore().apply {
            profiles = listOf(Profile("a", "Ada"), Profile("b", "Ben"))
            chosen = "a"
        }
    private val core =
        StatsCore(raw).apply {
            answers["a"] = summary(all = 600.0)
            answers["b"] = summary(all = 1_800.0)
        }
    private val watch = DefaultWatchStateRepository(FakeCoreProvider(core), Dispatchers.Unconfined)

    private val seen = InMemoryAchievementsSeen()

    private fun model() = StatsViewModel(FakeCoreProvider(core), watch, seen).apply { now = { Now } }

    private fun allSeconds(read: StatsRead) = assertIs<StatsRead.Done>(read).summary.allSeconds

    @Test
    fun theChosenProfilesStatsAreReadForTodayOnThisDevicesClock() =
        runTest {
            watch.reload()

            val done = assertIs<StatsRead.Done>(model().state.first { it != StatsRead.Loading })

            assertEquals(listOf("a" to "2026-09-26"), core.asked)
            assertEquals(600.0, done.summary.allSeconds)
            assertEquals(Now, done.now, "the read's clock is the one every line is told on")
        }

    @Test
    fun aProfileSwitchedMidReadShowsOnlyTheNewProfilesStats() =
        runTest {
            watch.reload()
            core.holdA = CompletableDeferred()
            val model = model()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
            assertEquals(StatsRead.Loading, model.state.value, "a's read is held open")

            watch.chooseProfile("b")
            advanceUntilIdle()
            assertEquals(1_800.0, allSeconds(model.state.value), "b's stats show without waiting on a's read")

            core.holdA!!.complete(Unit)
            advanceUntilIdle()
            assertEquals(1_800.0, allSeconds(model.state.value), "a's read, finishing late, is never published")
        }

    @Test
    fun aReturnVisitReadsAgainAndNeverShowsTheProfileItLeft() =
        runTest {
            watch.reload()
            val model = model()
            val visit = backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
            advanceUntilIdle()
            assertEquals(600.0, allSeconds(model.state.value))

            visit.cancel()
            advanceTimeBy(5_001)
            assertEquals(StatsRead.Loading, model.state.value, "a's stats are not kept for whoever opens the page next")

            watch.chooseProfile("b")
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
            advanceUntilIdle()
            assertEquals(1_800.0, allSeconds(model.state.value))
            assertEquals(listOf("a", "b"), core.asked.map { it.first })
        }

    @Test
    fun withNobodyChosenNothingIsRead() =
        runTest {
            raw.chosen = null
            watch.reload()
            val model = model()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
            advanceUntilIdle()

            assertEquals(StatsRead.Loading, model.state.value)
            assertTrue(core.asked.isEmpty())
        }

    /** An exception's own text names internals; only a sentence the core wrote is ever shown. */
    @Test
    fun aCoreThatCannotBeReachedFailsWithoutAnExceptionsText() =
        runTest {
            watch.reload()
            // The core itself never throws from stats() (a storage failure answers an
            // empty summary); what can fail is reaching a core at all.
            val model = StatsViewModel(FakeCoreProvider(null), watch, seen).apply { now = { Now } }

            assertEquals(StatsRead.Failed(null), model.state.first { it != StatsRead.Loading })
        }

    @Test
    fun theChosenProfilesAchievementsArriveWithItsStats() =
        runTest {
            raw.achievementsByProfile =
                mapOf(
                    "a" to
                        Achievements(
                            earned = listOf(EarnedAchievement(id = "films-1", earnedAt = ms(2026, 9, 26, 21, 0))),
                            next = listOf(NextAchievement(id = "films-10", have = 1u, need = 10u)),
                        ),
                )
            watch.reload()

            val done = assertIs<StatsRead.Done>(model().state.first { it != StatsRead.Loading })

            assertEquals(listOf("films-1"), done.achievements.earned.map { it.id })
            assertEquals(listOf("films-10"), done.achievements.next.map { it.id })
            assertEquals(Triple("a", "2026-09-26", 120), raw.achievementsAsked.last())
        }

    @Test
    fun markingSeenRecordsWhatThePageWasShownForItsProfile() =
        runTest {
            raw.achievementsByProfile =
                mapOf("a" to Achievements(earned = listOf(EarnedAchievement("films-1", 1L), EarnedAchievement("genres-5", 2L)), next = emptyList()))
            watch.reload()
            val model = model()
            model.state.first { it is StatsRead.Done }

            model.markAchievementsSeen()

            assertEquals(mapOf("a" to setOf("films-1", "genres-5")), seen.seen.value)
        }

    @Test
    fun markingSeenBeforeAnythingWasReadRecordsNothing() {
        model().markAchievementsSeen()
        assertEquals(emptyMap(), seen.seen.value)
    }

    @Test
    fun aSwitchToAProfileStillBeingReadMarksNothingForTheOneLeft() =
        runTest {
            raw.chosen = "b"
            raw.achievementsByProfile = mapOf("b" to Achievements(earned = listOf(EarnedAchievement("films-1", 1L)), next = emptyList()))
            watch.reload()
            val model = model()
            backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) { model.state.collect {} }
            advanceUntilIdle()
            assertIs<StatsRead.Done>(model.state.value)

            core.holdA = CompletableDeferred()
            watch.chooseProfile("a")
            advanceUntilIdle()
            model.markAchievementsSeen()

            assertEquals(emptyMap(), seen.seen.value, "Ben's achievements are not marked again once Ada's page is being read")
        }
}
