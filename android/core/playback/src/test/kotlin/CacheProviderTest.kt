@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.content.Context
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.io.RandomAccessFile
import java.util.concurrent.Executors
import kotlin.coroutines.CoroutineContext
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

/** Delegates every dispatch to [delegate], recording the thread each one actually ran on. */
private class RecordingDispatcher(
    private val delegate: CoroutineDispatcher,
) : CoroutineDispatcher() {
    @Volatile
    var lastDispatchThread: Thread? = null
        private set

    override fun dispatch(
        context: CoroutineContext,
        block: Runnable,
    ) {
        delegate.dispatch(context) {
            lastDispatchThread = Thread.currentThread()
            block.run()
        }
    }
}

/**
 * The locking behaviour a no-choice-recorded viewer must keep: today's
 * cache dir, the dispatcher contract, and a live budget change surviving a
 * reopen. Volume resolution and fallback are [CacheProviderVolumeTest]'s.
 */
@RunWith(RobolectricTestRunner::class)
class CacheProviderTest {
    private val probeExecutor = Executors.newSingleThreadExecutor()
    private var heldCache: SimpleCache? = null

    @Before
    fun resetTheSharedCache() {
        CacheProvider.resetForTest()
        val context = ApplicationProvider.getApplicationContext<Context>()
        // A deterministic single-internal-volume world by default.
        CacheProvider.volumesFor = { listOf(internalVolume(context)) }
    }

    @After
    fun shutDownTheProbeThread() {
        try {
            heldCache?.release()
        } finally {
            CacheProvider.resetForTest()
            probeExecutor.shutdown()
        }
    }

    @Test
    fun noChoiceRecordedOpensCacheDirMlibAndSurvivesAReopen() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dispatcher = probeExecutor.asCoroutineDispatcher()
            val expectedDir = File(context.cacheDir, "mlib")
            val initial = CacheProvider.get(context, dispatcher).also { heldCache = it }
            val file = commitSpan(initial, "kept")

            assertUnder(file, expectedDir)
            initial.release()
            heldCache = null
            CacheProvider.resetForTest()

            val reopened = CacheProvider.get(context, dispatcher).also { heldCache = it }

            assertTrue(reopened.isCached("kept", 0, MIN_CACHE_BYTES))
            val reopenedFile = assertNotNull(reopened.getCachedSpans("kept").single().file)
            assertUnder(reopenedFile, expectedDir)
        }

    @Test
    fun constructionRunsOnTheGivenDispatcherNotTheCallingThread() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val callingThread = Thread.currentThread()
            val recording = RecordingDispatcher(probeExecutor.asCoroutineDispatcher())

            heldCache = CacheProvider.get(context, recording)

            // Both halves matter: a dispatcher that's never actually invoked
            // would leave lastDispatchThread null, which is also "not equal
            // to callingThread" but proves nothing.
            assertNotNull(recording.lastDispatchThread, "the given dispatcher was never actually used")
            assertNotEquals(callingThread, recording.lastDispatchThread)
        }

    @Test
    fun loweringTheBudgetEvictsRealSpansAndTheChoiceSurvivesReopening() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dispatcher = probeExecutor.asCoroutineDispatcher()
            val volume = internalVolume(context)
            // The cap is fixed for a session at whatever was already held
            // when SimpleCache opened, not recomputed as more is cached —
            // the same "moving the cache needs a restart" reasoning that
            // fixes the volume itself for the process.
            val capAtFirstOpen = budgetCap(volume, heldBytes = 0)
            val initial = CacheProvider.get(context, dispatcher).also { heldCache = it }
            val first = commitSpan(initial, "first")
            val second = commitSpan(initial, "second")
            assertEquals(
                occupancyOn(volume, MIN_CACHE_BYTES * 2, CACHE_MAX_BYTES, capAtFirstOpen),
                CacheProvider.occupancy(context, dispatcher),
            )

            CacheProvider.setBudget(context, MIN_CACHE_BYTES, dispatcher)

            assertEquals(
                occupancyOn(volume, MIN_CACHE_BYTES, MIN_CACHE_BYTES, capAtFirstOpen),
                CacheProvider.occupancy(context, dispatcher),
            )
            assertEquals(MIN_CACHE_BYTES, PlainCacheBudgetSettings(context).read())
            assertTrue(first.exists() xor second.exists(), "shrinking must delete one real span file")
            val retainedKey = initial.keys.single()
            initial.release()
            heldCache = null
            CacheProvider.resetForTest()
            CacheProvider.volumesFor = { listOf(volume) }
            val capAtReopen = budgetCap(volume, heldBytes = MIN_CACHE_BYTES)

            val reopened = CacheProvider.get(context, dispatcher).also { heldCache = it }

            assertNotSame(initial, reopened)
            assertEquals(
                occupancyOn(volume, MIN_CACHE_BYTES, MIN_CACHE_BYTES, capAtReopen),
                CacheProvider.occupancy(context, dispatcher),
            )
            assertTrue(reopened.isCached(retainedKey, 0, MIN_CACHE_BYTES))
            val retainedFile = assertNotNull(reopened.getCachedSpans(retainedKey).single().file)
            RandomAccessFile(retainedFile, "r").use { persisted ->
                assertEquals(0x42, persisted.read())
                persisted.seek(MIN_CACHE_BYTES - 1)
                assertEquals(0x7f, persisted.read())
            }

            commitSpan(reopened, "replacement")

            assertFalse(reopened.isCached(retainedKey, 0, MIN_CACHE_BYTES))
            assertFalse(retainedFile.exists())
            assertTrue(reopened.isCached("replacement", 0, MIN_CACHE_BYTES))
            assertEquals(
                occupancyOn(volume, MIN_CACHE_BYTES, MIN_CACHE_BYTES, capAtReopen),
                CacheProvider.occupancy(context, dispatcher),
            )
        }

    private fun occupancyOn(volume: CacheVolume, heldBytes: Long, budgetBytes: Long, capBytes: Long) =
        CacheOccupancy(
            heldBytes = heldBytes,
            budgetBytes = budgetBytes,
            volumeLabel = volume.label,
            fellBack = false,
            capBytes = capBytes,
        )
}
