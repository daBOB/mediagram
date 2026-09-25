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

@RunWith(RobolectricTestRunner::class)
class CacheProviderTest {
    private val probeExecutor = Executors.newSingleThreadExecutor()
    private var heldCache: SimpleCache? = null

    @Before
    fun resetTheSharedCache() {
        CacheProvider.resetForTest()
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

            assertTrue(file.absolutePath.startsWith(expectedDir.absolutePath + File.separator), "$file is not under $expectedDir")
            initial.release()
            heldCache = null
            CacheProvider.resetForTest()

            val reopened = CacheProvider.get(context, dispatcher).also { heldCache = it }

            assertTrue(reopened.isCached("kept", 0, MIN_CACHE_BYTES))
            val reopenedFile = assertNotNull(reopened.getCachedSpans("kept").single().file)
            assertTrue(
                reopenedFile.absolutePath.startsWith(expectedDir.absolutePath + File.separator),
                "$reopenedFile is not under $expectedDir",
            )
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
            val initial = CacheProvider.get(context, dispatcher).also { heldCache = it }
            val first = commitSpan(initial, "first")
            val second = commitSpan(initial, "second")
            assertEquals(CacheOccupancy(MIN_CACHE_BYTES * 2, CACHE_MAX_BYTES), CacheProvider.occupancy(context, dispatcher))

            CacheProvider.setBudget(context, MIN_CACHE_BYTES, dispatcher)

            assertEquals(CacheOccupancy(MIN_CACHE_BYTES, MIN_CACHE_BYTES), CacheProvider.occupancy(context, dispatcher))
            assertEquals(MIN_CACHE_BYTES, PlainCacheBudgetSettings(context).read())
            assertTrue(first.exists() xor second.exists(), "shrinking must delete one real span file")
            val retainedKey = initial.keys.single()
            initial.release()
            heldCache = null
            CacheProvider.resetForTest()

            val reopened = CacheProvider.get(context, dispatcher).also { heldCache = it }

            assertNotSame(initial, reopened)
            assertEquals(CacheOccupancy(MIN_CACHE_BYTES, MIN_CACHE_BYTES), CacheProvider.occupancy(context, dispatcher))
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
            assertEquals(CacheOccupancy(MIN_CACHE_BYTES, MIN_CACHE_BYTES), CacheProvider.occupancy(context, dispatcher))
        }

    private fun commitSpan(
        cache: SimpleCache,
        key: String,
    ): File {
        val hole = cache.startReadWrite(key, 0, MIN_CACHE_BYTES)
        try {
            val file = cache.startFile(key, 0, MIN_CACHE_BYTES)
            // Sparse files exercise real persisted spans at the production budget
            // floor without allocating or writing hundreds of MiB of fixture data.
            RandomAccessFile(file, "rw").use { payload ->
                payload.setLength(MIN_CACHE_BYTES)
                payload.writeByte(0x42)
                payload.seek(MIN_CACHE_BYTES - 1)
                payload.writeByte(0x7f)
            }
            cache.commitFile(file, MIN_CACHE_BYTES)
            assertTrue(cache.isCached(key, 0, MIN_CACHE_BYTES))
            return file
        } finally {
            cache.releaseHoleSpan(hole)
        }
    }
}
