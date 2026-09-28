package playback

import kotlin.test.Test
import kotlin.test.assertEquals

private fun film(id: String, bytes: Long = 1_000L) = PreloadItem(id, "Film $id", bytes)

class FilmPreloadOverviewTest {

    @Test
    fun anEmptySnapshotIsAnEmptyList() {
        assertEquals(emptyList(), filmPreloadRows(QueueSnapshot(null, emptyList()), activeState = null))
    }

    @Test
    fun theActiveFilmLeadsThenEveryPendingFilmFollowsInOrder() {
        val snapshot = QueueSnapshot(film("f1"), listOf(film("f2"), film("f3")))

        val rows = filmPreloadRows(snapshot, FilmPreloadState.Running(400L, 1_000L))

        assertEquals(3, rows.size)
        assertEquals(FilmPreloadRow.Running("f1", "Film f1", 1_000L, heldBytes = 400L, pauseReason = null), rows[0])
        assertEquals(FilmPreloadRow.Waiting("f2", "Film f2", 1_000L), rows[1])
        assertEquals(FilmPreloadRow.Waiting("f3", "Film f3", 1_000L), rows[2])
    }

    @Test
    fun aPausedActiveFilmCarriesItsOwnReason() {
        val snapshot = QueueSnapshot(film("f1"), emptyList())

        val rows = filmPreloadRows(snapshot, FilmPreloadState.Paused(250L, 1_000L, PauseReason.Metered))

        assertEquals(FilmPreloadRow.Running("f1", "Film f1", 1_000L, heldBytes = 250L, pauseReason = PauseReason.Metered), rows.single())
    }

    /**
     * Dequeued, but still waiting for the shared lane a series preload can
     * hold for a whole episode's write — `stateOf` still answers `Queued`
     * for as long as that lasts, so this reads as still waiting, not as
     * running at zero: the same "never Running before the lane" rule
     * `FilmWriteAttempt` documents for itself.
     */
    @Test
    fun aDequeuedFilmStillWaitingForTheLaneReadsAsWaitingNotRunning() {
        val snapshot = QueueSnapshot(film("f1"), listOf(film("f2")))

        val rows = filmPreloadRows(snapshot, FilmPreloadState.Queued)

        assertEquals(FilmPreloadRow.Waiting("f1", "Film f1", 1_000L), rows[0])
        assertEquals(FilmPreloadRow.Waiting("f2", "Film f2", 1_000L), rows[1])
    }

    @Test
    fun withNothingActiveEveryPendingFilmIsWaiting() {
        val snapshot = QueueSnapshot(null, listOf(film("f2"), film("f3")))

        val rows = filmPreloadRows(snapshot, activeState = null)

        assertEquals(listOf(FilmPreloadRow.Waiting("f2", "Film f2", 1_000L), FilmPreloadRow.Waiting("f3", "Film f3", 1_000L)), rows)
    }
}
