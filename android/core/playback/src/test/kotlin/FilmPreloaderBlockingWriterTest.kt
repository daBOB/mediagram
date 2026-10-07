package playback

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.InterruptedIOException
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * A [FilmPreloadWriter] that blocks its own dedicated thread exactly the
 * way `CacheWriter.cache()` does — a real `Thread.sleep` loop, not a
 * suspend point — and is interruptible the same public way
 * [CacheDataSourceWriter.write] really is: a child coroutine on its own
 * dispatcher, joined from the caller, cancelled-and-rethrown on
 * `CancellationException`. A fake writer that merely suspended
 * (`CompletableDeferred.await()`) would prove nothing about whether a
 * real blocking write can actually be interrupted, which is the one thing
 * every test in this file is about. This fake also throws
 * `InterruptedIOException` on a deliberate cancel, an ordinary exception,
 * not a `CancellationException`, exactly like the real `CacheWriter` does.
 */
private class BlockingWriter(private val stepMs: Long = 15L, private val chunkBytes: Long = 50L) : PreloadWriter, FilmPreloadWriter {
    private val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()

    // Written on the write thread, read from the test's: thread-safe lists.
    val started = CopyOnWriteArrayList<String>()
    val completed = CopyOnWriteArrayList<String>()
    val cancelCount = AtomicInteger()

    /** Bytes the current write has reached — what a test waits on to know a write is under way rather than sleeping for it. */
    val written = AtomicLong()

    override suspend fun write(item: PreloadItem) = write(item) {}

    override suspend fun write(item: PreloadItem, onProgress: (Long) -> Unit) {
        val cancelled = AtomicBoolean(false)
        coroutineScope {
            val job =
                launch(dispatcher) {
                    started += item.setId
                    var bytes = 0L
                    written.set(bytes)
                    onProgress(bytes)
                    while (bytes < item.totalBytes) {
                        if (cancelled.get()) throw InterruptedIOException("cancelled")
                        Thread.sleep(stepMs)
                        bytes = minOf(bytes + chunkBytes, item.totalBytes)
                        written.set(bytes)
                        onProgress(bytes)
                    }
                    completed += item.setId
                }
            try {
                job.join()
            } catch (e: CancellationException) {
                cancelCount.incrementAndGet()
                cancelled.set(true)
                throw e
            }
        }
    }
}

private class MapHeldSets(private val holdings: MutableMap<String, Long> = mutableMapOf()) : HeldSetsQuery {
    override suspend fun isHeld(setId: String, totalBytes: Long) = (holdings[setId] ?: 0L) >= totalBytes
    override suspend fun heldIds(sets: List<Pair<String, Long>>) = emptySet<String>()
    override suspend fun heldBytes(setId: String, totalBytes: Long) = minOf(holdings[setId] ?: 0L, totalBytes)
}

private class BlockingTestOpenTitleSource(initial: OpenTitle? = null) : OpenTitleSource {
    private val _openTitle = MutableStateFlow(initial)
    override val openTitle: StateFlow<OpenTitle?> = _openTitle
    override suspend fun ensureListening() = Unit
    fun set(value: OpenTitle?) { _openTitle.value = value }
}

/**
 * Real threads, real dispatchers, real (short) waits — these prove what the
 * suspending-fake tests in `FilmPreloaderTest` structurally cannot: that a
 * `CacheWriter.cache()` blocking its own thread does not starve the pause,
 * cancel and remove paths, because `CacheDataSourceWriter.write` runs it on
 * its own dispatcher. And a watchdog's own cancel throws an ordinary
 * `InterruptedIOException`, not a `CancellationException`, so only a
 * writer shaped like this one can prove that is not counted as a failure.
 */
class FilmPreloaderBlockingWriterTest {

    private fun engine(
        writer: BlockingWriter,
        scope: CoroutineScope,
        heldSets: HeldSetsQuery = MapHeldSets(),
        openTitleSource: BlockingTestOpenTitleSource = BlockingTestOpenTitleSource(),
        removed: MutableList<String> = mutableListOf(),
        logs: MutableList<String> = mutableListOf(),
        fits: suspend (Long, Long) -> Boolean = { _, _ -> true },
    ) = FilmPreloader(
        scope = scope,
        dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher(),
        writer = writer,
        lane = DownloadLane(),
        heldSets = heldSets,
        removeFromCache = { removed += it },
        openTitleSource = openTitleSource,
        network = UnmeteredNetworkCheck { true },
        fits = fits,
        log = { line, _ -> logs += line },
    )

    /** A watchdog pausing the write must never be logged or counted as a write failure. */
    @Test
    fun anOpenTitlePausesARealBlockingWriteRatherThanFailingIt() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob())
            // Long enough (3s to finish naturally) that a passing test
            // proves real interruption, not a write that simply finished.
            val writer = BlockingWriter(stepMs = 15L, chunkBytes = 50L)
            val open = BlockingTestOpenTitleSource()
            val logs = mutableListOf<String>()
            val preloader = engine(writer, scope, openTitleSource = open, logs = logs)

            preloader.enqueue("f1", "Film f1", 10_000L)
            assertTrue(waitUntil { writer.started.isNotEmpty() }, "the write must start")
            assertTrue(waitUntil { writer.written.get() in 1 until 10_000L }, "the write must be under way, not finished")

            open.set(OpenTitle("other", 1L))
            val interruptedInTime = waitUntil(timeoutMs = 1_000) { writer.cancelCount.get() > 0 }
            assertTrue(interruptedInTime, "expected the write to be cancelled within a second of a title opening")

            val paused = waitUntil(timeoutMs = 1_000) { preloader.stateOf("f1", 10_000L).first() is FilmPreloadState.Paused }
            assertTrue(paused, "the pause must actually reach FilmPreloadState.Paused, not stay Running or go to Failed")
            assertFalse(logs.any { "write failed" in it }, "a watchdog pause must never be logged as a write failure: $logs")
            scope.cancel()
        }

    @Test
    fun cancelWhilePausedForAnOpenTitleActuallyStopsTheFilm() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob())
            val writer = BlockingWriter(stepMs = 10L, chunkBytes = 50L)
            val open = BlockingTestOpenTitleSource(OpenTitle("other", 1L))
            val preloader = engine(writer, scope, openTitleSource = open)

            preloader.enqueue("f1", "Film f1", 200L)
            assertTrue(waitUntil { preloader.stateOf("f1", 200L).first() is FilmPreloadState.Paused }, "the film must wait for the open title")

            preloader.cancel("f1")
            assertTrue(waitUntil { preloader.stateOf("f1", 200L).first() is FilmPreloadState.Idle }, "the cancel must settle the film to Idle")

            open.set(null)
            Thread.sleep(300) // long enough for a re-download to have finished, if one wrongly started

            assertTrue(writer.started.isEmpty(), "a cancelled-while-paused film must never have started writing at all")
            assertEquals(FilmPreloadState.Idle(0L, 200L), preloader.stateOf("f1", 200L).first())
            scope.cancel()
        }

    @Test
    fun removeWhilePausedClearsTheCacheAndNeverReDownloads() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob())
            val writer = BlockingWriter(stepMs = 10L, chunkBytes = 50L)
            val open = BlockingTestOpenTitleSource(OpenTitle("other", 1L))
            val removed = mutableListOf<String>()
            val preloader = engine(writer, scope, openTitleSource = open, removed = removed)
            val unheld = mutableListOf<String>()
            val heldEvents = mutableListOf<String>()
            scope.launch { preloader.unheldEvents.collect { unheld += it } }
            scope.launch { preloader.heldEvents.collect { heldEvents += it } }

            preloader.enqueue("f1", "Film f1", 200L)
            assertTrue(waitUntil { preloader.stateOf("f1", 200L).first() is FilmPreloadState.Paused }, "the film must wait for the open title")

            preloader.remove("f1")
            assertTrue(waitUntil { removed.contains("f1") }, "the remove must clear the film from the cache")

            open.set(null)
            Thread.sleep(300)

            assertTrue(writer.started.isEmpty(), "removed while paused must never start writing")
            assertTrue(heldEvents.isEmpty())
            assertEquals(listOf("f1"), unheld)
            scope.cancel()
        }

    @Test
    fun cancelWhileWaitingForTheLaneNeverTouchesTheSeriesWrite() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob())
            val writer = BlockingWriter(stepMs = 10L, chunkBytes = 40L)
            val lane = DownloadLane()
            val dispatcher = Executors.newSingleThreadExecutor().asCoroutineDispatcher()
            val series =
                SeriesPreloader(scope, dispatcher, writer, isHeld = { false }, fits = { _, _ -> true }, lane = lane)
            val preloader =
                FilmPreloader(
                    scope = scope,
                    dispatcher = dispatcher,
                    writer = writer,
                    lane = lane,
                    heldSets = MapHeldSets(),
                    removeFromCache = {},
                    openTitleSource = BlockingTestOpenTitleSource(),
                    network = UnmeteredNetworkCheck { true },
                    fits = { _, _ -> true },
                )

            // 20 steps at 10ms — still writing when the film's cancel below
            // arrives, done well inside waitUntil's own default timeout.
            series.want(listOf(PreloadItem("ep2", "E2", 800L)), 0L)
            assertTrue(waitUntil { writer.started.contains("ep2") }, "the series write must start")

            preloader.enqueue("f1", "Film f1", 200L)
            Thread.sleep(50) // f1 is now queued behind ep2's write, waiting for the lane

            preloader.cancel("f1")
            val finished = waitUntil { writer.completed.contains("ep2") }

            assertEquals(0, writer.cancelCount.get(), "cancelling a film waiting for the lane must never reach the series write")
            assertTrue(finished, "ep2 must finish undisturbed")
            scope.cancel()
        }

    @Test
    fun cancelDuringAnActiveWriteSettlesCleanlyRatherThanStayingStaleRunning() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob())
            val writer = BlockingWriter(stepMs = 20L, chunkBytes = 30L)
            val preloader = engine(writer, scope)

            preloader.enqueue("f1", "Film f1", 1_000_000L)
            assertTrue(waitUntil { writer.started.isNotEmpty() }, "the write must start")
            assertTrue(waitUntil { writer.written.get() in 1 until 1_000_000L }, "the write must be under way, some bytes already progressed")

            preloader.cancel("f1")
            val settled = waitUntil(timeoutMs = 1_000) { preloader.stateOf("f1", 1_000_000L).first() !is FilmPreloadState.Running }

            assertTrue(settled, "must not stay stuck at a stale Running after cancel")
            assertEquals(null, preloader.active.value)
            scope.cancel()
        }

    /** The fits check must not treat a transient open-title reserve as a permanent verdict. */
    @Test
    fun openingATitleThatOnlyTransientlyExceedsTheBudgetPausesRatherThanNeedsSpace() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob())
            // 8 bytes at a 50-byte chunk would finish in a single 10ms
            // step — racing open.set() below, called from a different
            // thread right after waitUntil returns, with no margin at
            // all. Scaled up (same fits ratios) so the first write is
            // still genuinely in flight when the title opens.
            val writer = BlockingWriter(stepMs = 10L, chunkBytes = 50L)
            val open = BlockingTestOpenTitleSource()
            val budget = 1_200L
            // stateOf re-verifies Done against heldSets fresh (a lowered
            // budget or later eviction must not leave a stale checkmark) —
            // a MapHeldSets that nothing ever writes to would make that
            // re-verification always fail, downgrading straight back to
            // Idle right after the write genuinely completes. writer.completed
            // is what actually happened, so mirror it rather than fake it.
            val heldSets =
                object : HeldSetsQuery {
                    override suspend fun isHeld(setId: String, totalBytes: Long) = writer.completed.contains(setId)

                    override suspend fun heldIds(sets: List<Pair<String, Long>>) =
                        sets.map { it.first }.filter { writer.completed.contains(it) }.toSet()

                    override suspend fun heldBytes(setId: String, totalBytes: Long) =
                        if (writer.completed.contains(setId)) totalBytes else 0L
                }
            val preloader =
                engine(
                    writer,
                    scope,
                    heldSets = heldSets,
                    openTitleSource = open,
                    fits = { total, reserved -> fitsFilmPreloadBudget(total, reserved, budget) },
                )

            preloader.enqueue("b", "Film B", 800L)
            assertTrue(waitUntil { writer.started.contains("b") }, "B's write must start")
            // Genuinely mid-write before C opens, not racing the very first chunk.
            assertTrue(waitUntil { writer.written.get() in 1 until 800L }, "B's write must be under way, not finished")

            // 800 (B) + 500 (C) = 1300 > 1200: does not fit with C's
            // reserve, but 800 alone does — must wait for C, not report
            // NeedsSpace.
            open.set(OpenTitle("c", 500L))
            val paused = waitUntil { preloader.stateOf("b", 800L).first() is FilmPreloadState.Paused }
            assertTrue(paused, "must pause for the open title rather than a terminal NeedsSpace, since it fits once C closes")

            open.set(null)
            val done = waitUntil(timeoutMs = 3_000) { preloader.stateOf("b", 800L).first() == FilmPreloadState.Done }
            assertTrue(done, "must resume and finish once the transient reserve is gone")
            scope.cancel()
        }

    /** The queue slot must be free the instant cancel() returns, not only once the cancelled write has fully unwound. */
    @Test
    fun cancelThenImmediateReEnqueueIsNotLost() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob())
            val writer = BlockingWriter(stepMs = 20L, chunkBytes = 30L)
            val preloader = engine(writer, scope)

            preloader.enqueue("f1", "Film f1", 1_000_000L)
            assertTrue(waitUntil { writer.started.isNotEmpty() }, "the write must start")
            assertTrue(waitUntil { writer.written.get() in 1 until 1_000_000L }, "the write must be under way, not finished")

            preloader.cancel("f1")
            preloader.enqueue("f1", "Film f1", 1_000_000L) // viewer taps Preload again right away

            val queuedOrRunning =
                waitUntil(timeoutMs = 1_000) {
                    val state = preloader.stateOf("f1", 1_000_000L).first()
                    state is FilmPreloadState.Queued || state is FilmPreloadState.Running
                }
            assertTrue(queuedOrRunning, "an immediate re-enqueue after cancel must not be silently dropped")
            assertTrue(preloader.hasWork.value)
            scope.cancel()
        }

    /** Android's own time limit on the foreground service pauses every queued film, not only the one actually writing. */
    @Test
    fun pauseForTimeLimitPausesEveryQueuedFilmNotJustTheActiveOne() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob())
            val writer = BlockingWriter(stepMs = 20L, chunkBytes = 20L)
            val preloader = engine(writer, scope)

            preloader.enqueue("a", "Film A", 1_000_000L)
            assertTrue(waitUntil { writer.started.contains("a") }, "A's write must start")
            preloader.enqueue("b", "Film B", 500L)
            assertTrue(waitUntil { preloader.stateOf("b", 500L).first() is FilmPreloadState.Queued }, "B must queue behind A")

            preloader.pauseForTimeLimit()

            val aPaused =
                waitUntil(timeoutMs = 1_000) {
                    (preloader.stateOf("a", 1_000_000L).first() as? FilmPreloadState.Paused)?.reason == PauseReason.TimeLimit
                }
            val bPaused =
                waitUntil(timeoutMs = 1_000) {
                    (preloader.stateOf("b", 500L).first() as? FilmPreloadState.Paused)?.reason == PauseReason.TimeLimit
                }
            assertTrue(aPaused, "the active film must pause for the time limit")
            assertTrue(bPaused, "a merely queued film must also pause for it, not silently start running once the active one stops")
            assertTrue(waitUntil(timeoutMs = 1_000) { !preloader.hasWork.value }, "nothing should be left queued")
            scope.cancel()
        }

    /** A Preloads page has nowhere else to read these once the real queue is emptied by the pause itself — [FilmPreloader.timeLimitPaused] is that list. */
    @Test
    fun timeLimitPausedReportsBothFilmsThenDropsOneOnceItIsResumed() =
        runBlocking {
            val scope = CoroutineScope(SupervisorJob())
            val writer = BlockingWriter(stepMs = 20L, chunkBytes = 20L)
            val preloader = engine(writer, scope)

            preloader.enqueue("a", "Film A", 1_000_000L)
            assertTrue(waitUntil { writer.started.contains("a") }, "A's write must start")
            preloader.enqueue("b", "Film B", 500L)
            assertTrue(waitUntil { preloader.stateOf("b", 500L).first() is FilmPreloadState.Queued }, "B must queue behind A")

            preloader.pauseForTimeLimit()
            assertTrue(waitUntil(timeoutMs = 1_000) { preloader.timeLimitPaused.value.size == 2 })
            val paused = preloader.timeLimitPaused.value.associateBy { it.setId }
            assertTrue(paused.getValue("a").wasActive, "the film that was actually writing is marked as such")
            assertFalse(paused.getValue("b").wasActive, "a merely queued film is not")

            preloader.enqueue("a", "Film A", 1_000_000L)
            val onlyBLeft =
                waitUntil(timeoutMs = 1_000) {
                    preloader.timeLimitPaused.value.map { it.setId } == listOf("b")
                }
            assertTrue(onlyBLeft, "resuming a takes it out of the paused list, leaving the film that is still waiting")
            scope.cancel()
        }
}

/** Polls [condition] until it is true or [timeoutMs] elapses — the plain, dependency-free way to await a real background coroutine's effect without a virtual clock. */
private suspend fun waitUntil(timeoutMs: Long = 2_000, stepMs: Long = 10, condition: suspend () -> Boolean): Boolean {
    val until = System.currentTimeMillis() + timeoutMs
    while (System.currentTimeMillis() < until) {
        if (condition()) return true
        Thread.sleep(stepMs)
    }
    return condition()
}
