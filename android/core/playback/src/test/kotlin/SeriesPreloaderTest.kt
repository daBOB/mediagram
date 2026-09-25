package playback

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Records what it was asked to write, with an optional gate a test can hold open to inspect an item "in flight". */
private class FakeWriter : PreloadWriter {
    val started = mutableListOf<String>()
    val written = mutableListOf<String>()
    var gate: CompletableDeferred<Unit>? = null

    override suspend fun write(item: PreloadItem) {
        started += item.setId
        gate?.await()
        written += item.setId
    }
}

private fun ep(id: String, bytes: Long = 100L) = PreloadItem(id, "Episode $id", bytes)

class SeriesPreloaderTest {

    private fun preloader(
        writer: PreloadWriter,
        scope: kotlinx.coroutines.CoroutineScope,
        held: Set<String> = emptySet(),
        fits: Boolean = true,
    ) = SeriesPreloader(
        scope = scope,
        dispatcher = Dispatchers.Unconfined,
        writer = writer,
        isHeld = { it.setId in held },
        fits = { _, _ -> fits },
    )

    @Test
    fun takesEveryWantedItemInOrder() = runTest {
        val writer = FakeWriter()
        val preloader = preloader(writer, backgroundScope)

        preloader.want(listOf(ep("s2"), ep("s3")), currentPlayingBytes = 0)

        assertEquals(listOf("s2", "s3"), writer.written)
    }

    /**
     * A viewer opening a different episode calls [SeriesPreloader.want]
     * again before the first list has finished; the item already
     * downloading is let finish (it is usually still wanted, and half of
     * it is already on disk) but nothing behind it in the old list is
     * ever started.
     */
    @Test
    fun aLaterWantReplacesTheWaitingListButLetsTheCurrentDownloadFinish() = runTest {
        val writer = FakeWriter()
        val gate = CompletableDeferred<Unit>()
        writer.gate = gate
        val preloader = preloader(writer, backgroundScope)

        preloader.want(listOf(ep("s2"), ep("s3")), currentPlayingBytes = 0)
        // s2 is now "in flight", held open by the gate; s3 is still waiting.
        assertEquals(listOf("s2"), writer.started)

        preloader.want(listOf(ep("s5"), ep("s6")), currentPlayingBytes = 0)
        gate.complete(Unit)

        assertEquals(listOf("s2", "s5", "s6"), writer.started)
        assertTrue("s3" !in writer.started, "s3 was dropped by the newer want(), not started after s2")
    }

    @Test
    fun anItemAlreadyDownloadingIsNotDuplicatedByAWantNamingItAgain() = runTest {
        val writer = FakeWriter()
        val gate = CompletableDeferred<Unit>()
        writer.gate = gate
        val preloader = preloader(writer, backgroundScope)

        preloader.want(listOf(ep("s2")), currentPlayingBytes = 0)
        preloader.want(listOf(ep("s2"), ep("s3")), currentPlayingBytes = 0)
        gate.complete(Unit)

        assertEquals(listOf("s2", "s3"), writer.started)
    }

    @Test
    fun skipsAnItemAlreadyHeld() = runTest {
        val writer = FakeWriter()
        val preloader = preloader(writer, backgroundScope, held = setOf("s2"))

        preloader.want(listOf(ep("s2"), ep("s3")), currentPlayingBytes = 0)

        assertEquals(listOf("s3"), writer.written)
    }

    @Test
    fun skipsAnItemThatDoesNotFitTheBudget() = runTest {
        val writer = FakeWriter()
        val preloader = preloader(writer, backgroundScope, fits = false)

        preloader.want(listOf(ep("s2")), currentPlayingBytes = 0)

        assertTrue(writer.written.isEmpty())
    }

    @Test
    fun emitsAHeldEventOnceAWriteFinishes() = runTest {
        val writer = FakeWriter()
        val preloader = preloader(writer, backgroundScope)
        val events = mutableListOf<String>()
        backgroundScope.launch { preloader.heldEvents.collect { events += it } }
        runCurrent() // lets the collector actually subscribe before anything is emitted

        preloader.want(listOf(ep("s2")), currentPlayingBytes = 0)
        runCurrent() // lets the collector's own dispatcher process the emission

        assertEquals(listOf("s2"), events)
    }
}
