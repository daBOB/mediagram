package playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import java.io.IOException
import java.io.InterruptedIOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * A [FilmPreloadWriter] that suspends rather than blocks — fine for these
 * logic tests, which drive everything through a virtual clock and never
 * exercise real thread interruption; see `FilmPreloaderBlockingWriterTest`
 * for the writer that blocks its own thread the way `CacheWriter.cache()`
 * really does. [gate], held open, lets a test catch an item "in flight".
 */
private class FakeFilmWriter(val holdings: MutableMap<String, Long> = mutableMapOf()) : FilmPreloadWriter {
    val started = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null
    var failNextAttemptsWith: Int = 0

    override suspend fun write(item: PreloadItem, onProgress: (bytesCached: Long) -> Unit) {
        started += item.setId
        gate?.await()
        if (failNextAttemptsWith > 0) {
            failNextAttemptsWith--
            throw IOException("simulated transient failure")
        }
        // The real Cache genuinely holds every byte a completed CacheWriter
        // wrote; [holdings] is this fake's stand-in for that disk state, so
        // a paired FakeHeldSets sees the same truth stateOf's own
        // Done-recheck reads back (see FilmPreloader.stateOf).
        holdings[item.setId] = item.totalBytes
        onProgress(item.totalBytes)
    }
}

private class FakeHeldSets(private val holdings: MutableMap<String, Long> = mutableMapOf()) : HeldSetsQuery {
    override suspend fun isHeld(setId: String, totalBytes: Long) = (holdings[setId] ?: 0L) >= totalBytes
    override suspend fun heldIds(sets: List<Pair<String, Long>>) =
        sets.filter { (id, total) -> (holdings[id] ?: 0L) >= total }.mapTo(mutableSetOf()) { it.first }
    override suspend fun heldBytes(setId: String, totalBytes: Long) = minOf(holdings[setId] ?: 0L, totalBytes)
}

private class FakeOpenTitleSource(initial: OpenTitle? = null) : OpenTitleSource {
    private val _openTitle = MutableStateFlow(initial)
    override val openTitle: StateFlow<OpenTitle?> = _openTitle
    override suspend fun ensureListening() = Unit
    fun set(value: OpenTitle?) { _openTitle.value = value }
}

class FilmPreloaderTest {

    /**
     * [UnconfinedTestDispatcher], not the plain
     * [kotlinx.coroutines.Dispatchers.Unconfined] `SeriesPreloaderTest`
     * uses: this preloader's own `delay()` calls (the metered recheck, the
     * retry backoff) need a dispatcher [advanceTimeBy] actually understands,
     * which the plain one is not.
     */
    private fun TestScope.engine(
        writer: FilmPreloadWriter,
        heldSets: HeldSetsQuery = (writer as? FakeFilmWriter)?.let { FakeHeldSets(it.holdings) } ?: FakeHeldSets(),
        openTitleSource: FakeOpenTitleSource = FakeOpenTitleSource(),
        unmetered: Boolean = true,
        fits: Boolean = true,
        removed: MutableList<String> = mutableListOf(),
    ) = FilmPreloader(
        scope = backgroundScope,
        dispatcher = UnconfinedTestDispatcher(testScheduler),
        writer = writer,
        lane = DownloadLane(),
        heldSets = heldSets,
        removeFromCache = { removed += it },
        openTitleSource = openTitleSource,
        network = UnmeteredNetworkCheck { unmetered },
        fits = { _, _ -> fits },
    )

    @Test
    fun enqueueTakesAFilmThroughRunningToDone() = runTest {
        val writer = FakeFilmWriter()
        writer.gate = CompletableDeferred()
        val preloader = engine(writer)

        preloader.enqueue("f1", "Film f1", 1_000L)
        runCurrent()

        assertEquals(listOf("f1"), writer.started)
        assertEquals(FilmPreloadState.Running(0L, 1_000L), preloader.stateOf("f1", 1_000L).first())

        writer.gate!!.complete(Unit)
        runCurrent()

        assertEquals(FilmPreloadState.Done, preloader.stateOf("f1", 1_000L).first())
    }

    @Test
    fun aSecondFilmWaitsQueuedWhileTheFirstIsActive() = runTest {
        val writer = FakeFilmWriter()
        writer.gate = CompletableDeferred()
        val preloader = engine(writer)

        preloader.enqueue("f1", "Film f1", 1_000L)
        runCurrent()
        preloader.enqueue("f2", "Film f2", 500L)
        runCurrent()

        assertEquals(listOf("f1"), writer.started)
        assertEquals(FilmPreloadState.Queued, preloader.stateOf("f2", 500L).first())
    }

    @Test
    fun enqueueIsANoOpForAFilmAlreadyQueuedOrActive() = runTest {
        val writer = FakeFilmWriter()
        writer.gate = CompletableDeferred()
        val preloader = engine(writer)

        preloader.enqueue("f1", "Film f1", 1_000L)
        runCurrent()
        preloader.enqueue("f1", "Film f1", 1_000L)
        runCurrent()

        assertEquals(listOf("f1"), writer.started)
    }

    /** A total of zero or less is rejected outright — the same rule `HeldSetsQuery.isHeld` already gives a set of unknown size. */
    @Test
    fun aNonPositiveTotalIsRejected() = runTest {
        val writer = FakeFilmWriter()
        val preloader = engine(writer)
        val events = mutableListOf<String>()
        backgroundScope.launch { preloader.heldEvents.collect { events += it } }
        runCurrent()

        preloader.enqueue("f0", "Film f0", 0L)
        runCurrent()

        assertTrue(writer.started.isEmpty())
        assertTrue(events.isEmpty())
        assertEquals(FilmPreloadState.Idle(0L, 0L), preloader.stateOf("f0", 0L).first())
    }

    @Test
    fun emitsAHeldEventOnceAWriteFinishes() = runTest {
        val writer = FakeFilmWriter()
        val preloader = engine(writer)
        val events = mutableListOf<String>()
        backgroundScope.launch { preloader.heldEvents.collect { events += it } }
        runCurrent()

        preloader.enqueue("f1", "Film f1", 1_000L)
        runCurrent()

        assertEquals(listOf("f1"), events)
    }

    @Test
    fun aFilmAlreadyFullyHeldFinishesWithoutWriting() = runTest {
        val writer = FakeFilmWriter()
        val heldSets = FakeHeldSets(mutableMapOf("f1" to 1_000L))
        val preloader = engine(writer, heldSets = heldSets)

        preloader.enqueue("f1", "Film f1", 1_000L)
        runCurrent()

        assertTrue(writer.started.isEmpty())
        assertEquals(FilmPreloadState.Done, preloader.stateOf("f1", 1_000L).first())
    }

    /** A film fully held from playback alone, enqueued on a metered network, is Done — the metered gate never gets a say, because held is checked first. */
    @Test
    fun aFullyHeldFilmIsDoneEvenOnAMeteredNetwork() = runTest {
        val writer = FakeFilmWriter()
        val heldSets = FakeHeldSets(mutableMapOf("f1" to 1_000L))
        val preloader = engine(writer, heldSets = heldSets, unmetered = false)

        preloader.enqueue("f1", "Film f1", 1_000L)
        runCurrent()

        assertEquals(FilmPreloadState.Done, preloader.stateOf("f1", 1_000L).first())
    }

    @Test
    fun aMeteredNetworkPausesRatherThanFails() = runTest {
        val writer = FakeFilmWriter()
        val preloader = engine(writer, unmetered = false)

        preloader.enqueue("f1", "Film f1", 1_000L)
        runCurrent()

        val state = preloader.stateOf("f1", 1_000L).first()
        assertEquals(FilmPreloadState.Paused(0L, 1_000L, PauseReason.Metered), state)
        assertTrue(writer.started.isEmpty())
    }

    @Test
    fun aCandidateThatDoesNotFitTheBudgetNeedsSpace() = runTest {
        val writer = FakeFilmWriter()
        val preloader = engine(writer, fits = false)

        preloader.enqueue("f1", "Film f1", 1_000L)
        runCurrent()

        assertEquals(FilmPreloadState.NeedsSpace(1_000L), preloader.stateOf("f1", 1_000L).first())
        assertTrue(writer.started.isEmpty())
    }

    @Test
    fun cancelStopsTheActiveWriteAndSettlesBackToIdle() = runTest {
        val writer = FakeFilmWriter()
        writer.gate = CompletableDeferred()
        val heldSets = FakeHeldSets(mutableMapOf("f1" to 400L))
        val preloader = engine(writer, heldSets = heldSets)

        preloader.enqueue("f1", "Film f1", 1_000L)
        runCurrent()

        preloader.cancel("f1")
        runCurrent()

        assertEquals(FilmPreloadState.Idle(400L, 1_000L), preloader.stateOf("f1", 1_000L).first())
    }

    @Test
    fun removeStopsTheWriteClearsTheCacheAndEmitsUnheld() = runTest {
        val writer = FakeFilmWriter()
        writer.gate = CompletableDeferred()
        val removed = mutableListOf<String>()
        val heldSets = FakeHeldSets(mutableMapOf("f1" to 400L))
        val preloader = engine(writer, heldSets = heldSets, removed = removed)
        val unheld = mutableListOf<String>()
        backgroundScope.launch { preloader.unheldEvents.collect { unheld += it } }
        runCurrent()

        preloader.enqueue("f1", "Film f1", 1_000L)
        runCurrent()

        preloader.remove("f1")
        runCurrent()

        assertEquals(listOf("f1"), removed)
        assertEquals(listOf("f1"), unheld)
    }

    @Test
    fun hasWorkTracksTheQueueEmptyingAsItsOnlyItemFinishes() = runTest {
        val writer = FakeFilmWriter()
        val preloader = engine(writer)

        assertTrue(!preloader.hasWork.value)
        preloader.enqueue("f1", "Film f1", 1_000L)
        runCurrent()

        assertTrue(!preloader.hasWork.value) // finished already, under the eager test dispatcher
    }

    /** A write that keeps failing with no forward progress gives up rather than retrying forever. */
    @Test
    fun givesUpAfterRepeatedFailuresWithNoForwardProgress() = runTest {
        val writer = FakeFilmWriter()
        writer.failNextAttemptsWith = Int.MAX_VALUE
        val preloader = engine(writer)

        preloader.enqueue("f1", "Film f1", 1_000L)
        // Each failure backs off before retrying; advance past every
        // possible delay so the whole retry budget plays out.
        advanceTimeBy(5 * 60_000L)
        runCurrent()

        assertIs<FilmPreloadState.Failed>(preloader.stateOf("f1", 1_000L).first())
    }

    /** A single transient failure — the shape a `FLOOD_WAIT` the core cannot name takes here — retries and still finishes, rather than failing outright. */
    @Test
    fun aSingleTransientFailureRetriesAndStillFinishes() = runTest {
        val writer = FakeFilmWriter()
        writer.failNextAttemptsWith = 1
        val preloader = engine(writer)

        preloader.enqueue("f1", "Film f1", 1_000L)
        advanceTimeBy(60_000L)
        runCurrent()

        assertEquals(FilmPreloadState.Done, preloader.stateOf("f1", 1_000L).first())
    }

    /** A budget check that throws (an unexpected failure from `fits`/`heldSets`) fails only that film, not every film queued after it. */
    @Test
    fun aThrowingDependencyFailsOnlyThatFilm() = runTest {
        val writer = FakeFilmWriter()
        var calls = 0
        val preloader =
            FilmPreloader(
                scope = backgroundScope,
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                writer = writer,
                lane = DownloadLane(),
                heldSets = FakeHeldSets(),
                removeFromCache = {},
                openTitleSource = FakeOpenTitleSource(),
                network = UnmeteredNetworkCheck { true },
                fits = { _, _ -> calls++; error("boom") },
            )

        preloader.enqueue("f1", "Film f1", 1_000L)
        runCurrent()
        preloader.enqueue("f2", "Film f2", 1_000L)
        runCurrent()

        assertEquals(2, calls, "one throw for f1 (which then fails), one for f2 (which is still tried, not blocked)")
        assertIs<FilmPreloadState.Failed>(preloader.stateOf("f1", 1_000L).first())
        assertTrue(!preloader.hasWork.value)
    }

    @Test
    fun queueOverviewListsTheRunningFilmFirstThenQueuedFilmsInOrder() = runTest {
        val writer = FakeFilmWriter()
        writer.gate = CompletableDeferred()
        val preloader = engine(writer)

        preloader.enqueue("f1", "Film f1", 1_000L)
        runCurrent()
        preloader.enqueue("f2", "Film f2", 500L)
        runCurrent()
        preloader.enqueue("f3", "Film f3", 300L)
        runCurrent()

        val rows = preloader.queueOverview.first()
        assertEquals(FilmPreloadRow.Running("f1", "Film f1", 1_000L, heldBytes = 0L, pauseReason = null), rows[0])
        assertEquals(FilmPreloadRow.Waiting("f2", "Film f2", 500L), rows[1])
        assertEquals(FilmPreloadRow.Waiting("f3", "Film f3", 300L), rows[2])
    }

    @Test
    fun queueOverviewDropsAFilmOnceCancelledAndPromotesTheNextOne() = runTest {
        val writer = FakeFilmWriter()
        writer.gate = CompletableDeferred()
        val preloader = engine(writer)

        preloader.enqueue("f1", "Film f1", 1_000L)
        runCurrent()
        preloader.enqueue("f2", "Film f2", 500L)
        runCurrent()

        preloader.cancel("f1")
        runCurrent()

        val rows = preloader.queueOverview.first()
        assertEquals("f2", rows.single().setId)
        assertIs<FilmPreloadRow.Running>(rows.single())
    }

    @Test
    fun theOpenTitleIsReservedOnlyWhenItIsADifferentFilm() = runTest {
        val writer = FakeFilmWriter()
        val open = FakeOpenTitleSource(OpenTitle("f1", 1_000L))
        var reservedSeen: Long? = null
        val preloader =
            FilmPreloader(
                scope = backgroundScope,
                dispatcher = UnconfinedTestDispatcher(testScheduler),
                writer = writer,
                lane = DownloadLane(),
                heldSets = FakeHeldSets(),
                removeFromCache = {},
                openTitleSource = open,
                network = UnmeteredNetworkCheck { true },
                fits = { _, reserved -> reservedSeen = reserved; true },
            )

        preloader.enqueue("f1", "Film f1", 1_000L)
        runCurrent()

        assertEquals(0L, reservedSeen, "the open title is this same film, so nothing is reserved against it")
    }
}
