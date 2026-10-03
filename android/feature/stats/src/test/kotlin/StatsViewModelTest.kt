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
import testing.ResolvedCoreProvider
import uniffi.mediagram_core.CoreInterface
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
    private val watch = DefaultWatchStateRepository(ResolvedCoreProvider(core), Dispatchers.Unconfined)

    private fun model() = StatsViewModel(ResolvedCoreProvider(core), watch).apply { now = { Now } }

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

    @Test
    fun aCoreThatCannotBeReachedFailsWithItsReason() =
        runTest {
            watch.reload()
            // The core itself never throws from stats() (a storage failure answers an
            // empty summary); what can fail is reaching a core at all.
            val model = StatsViewModel(FakeCoreProvider(null), watch).apply { now = { Now } }

            assertEquals(StatsRead.Failed("no core built for this fixture"), model.state.first { it != StatsRead.Loading })
        }
}
