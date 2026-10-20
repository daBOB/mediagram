package player

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import playback.FilmPreloadRow
import playback.FilmPreloadState
import playback.HeldSetsQuery
import playback.InMemoryLanCacheSettings
import testing.FakeFilmPreloading
import testing.FakeLanChunkProtocol
import testing.FakeLanServerSource
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * What an episode row reads beyond a film page: its own held share in every
 * state, and a "Queued · after …" that never carries another episode's figure
 * nor loses the episode code to the ellipsis.
 */
class EpisodeRowPreloadTest {
    private val preloading = FakeFilmPreloading()

    /** 42 bytes of anything held, and how often it was asked. */
    private val cache =
        object : HeldSetsQuery {
            var reads = 0

            override suspend fun isHeld(setId: String, totalBytes: Long) = false

            override suspend fun heldIds(sets: List<Pair<String, Long>>): Set<String> = emptySet()

            override suspend fun heldBytes(setId: String, totalBytes: Long): Long {
                reads++
                return 42L
            }
        }

    private fun viewModel() =
        TitlePreloadViewModel(preloading, InMemoryLanCacheSettings(), FakeLanServerSource(), FakeLanChunkProtocol(), heldSets = cache)

    @Test
    fun heldBytesReadsTheCacheWhileQueuedAndTheLiveFigureWhileRunning() = runTest {
        preloading.setState("e1", 100L, FilmPreloadState.Queued)
        viewModel().heldBytes("e1", 100L).test {
            assertEquals(42L, awaitItem())
            for (held in 50L..60L) preloading.setState("e1", 100L, FilmPreloadState.Running(held, 100L))
            assertEquals(60L, expectMostRecentItem())
            preloading.setState("e1", 100L, FilmPreloadState.Done)
            assertEquals(100L, awaitItem())
        }
        assertEquals(1, cache.reads, "progress ticks never ask the cache")
    }

    @Test
    fun queuedAheadOnAnEpisodeRowLeavesOutTheOtherEpisodesShare() = runTest {
        preloading.setState("e2", 100L, FilmPreloadState.Queued)
        preloading.setQueueOverview(
            listOf(
                FilmPreloadRow.Running("e1", "Show · S1E1", 100L, heldBytes = 36L, pauseReason = null),
                FilmPreloadRow.Waiting("e2", "Show · S1E2", 100L),
            ),
        )
        viewModel().queuedAhead("e2", 100L, withShare = false).test { assertEquals("Queued · after Show · S1E1", awaitItem()) }
    }

    @Test
    fun queuedAheadKeepsTheEpisodeCodeWholeAndShortensTheShow() {
        fun after(running: String) =
            queuedAheadLabel(
                listOf(
                    FilmPreloadRow.Running("e1", running, 100L, heldBytes = 40L, pauseReason = null),
                    FilmPreloadRow.Waiting("e2", "Next", 100L),
                ),
                "e2",
            )
        assertEquals("Queued · after Game of Thro… · S1E4, 40%", after("Game of Thrones · S1E4"))
        assertEquals("Queued · after Brooklyn Nin… · S1E4, 40%", after("Brooklyn Nine-Nine · S1E4"))
        assertEquals("Queued · after Show · S1E4, 40%", after("Show · S1E4"))
    }
}
