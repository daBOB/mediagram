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
import java.nio.file.Files
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

/** A generous, fixed free-space figure so a test's expected [CacheOccupancy.capBytes] never depends on the host's real disk. */
private const val FAKE_FREE_BYTES = 100L * 1024 * 1024 * 1024

private fun internalVolume(context: Context, freeBytes: Long = FAKE_FREE_BYTES) =
    CacheVolume(INTERNAL_VOLUME_ID, "Internal storage", File(context.cacheDir, "mlib"), freeBytes, removable = false)

/** A volume rooted at a fresh temp directory, standing in for an SD card or USB drive under Robolectric. */
private fun tempVolume(id: String, freeBytes: Long = FAKE_FREE_BYTES): CacheVolume {
    val root = Files.createTempDirectory("cache-volume-$id-").toFile()
    return CacheVolume(id, id, File(root, "mlib"), freeBytes, removable = true)
}

@RunWith(RobolectricTestRunner::class)
class CacheProviderTest {
    private val probeExecutor = Executors.newSingleThreadExecutor()
    private var heldCache: SimpleCache? = null

    @Before
    fun resetTheSharedCache() {
        CacheProvider.resetForTest()
        val context = ApplicationProvider.getApplicationContext<Context>()
        // A deterministic single-internal-volume world by default; tests that
        // need an external or a broken one override this themselves.
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

    @Test
    fun openingWithAPresentChosenVolumeUsesThatVolumesDirectory() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dispatcher = probeExecutor.asCoroutineDispatcher()
            val external = tempVolume("card")
            CacheProvider.volumesFor = { listOf(internalVolume(context), external) }
            PlainCacheVolumeSettings(context).write("card")

            val cache = CacheProvider.get(context, dispatcher).also { heldCache = it }
            val file = commitSpan(cache, "kept")

            assertUnder(file, external.dir)
            val occupancy = CacheProvider.occupancy(context, dispatcher)
            assertEquals("card", occupancy.volumeLabel)
            assertFalse(occupancy.fellBack)
        }

    @Test
    fun aChosenVolumeThatIsAbsentFallsBackToInternalAndKeepsTheRecordedChoice() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dispatcher = probeExecutor.asCoroutineDispatcher()
            CacheProvider.volumesFor = { listOf(internalVolume(context)) } // "card" not present
            PlainCacheVolumeSettings(context).write("card")

            val cache = CacheProvider.get(context, dispatcher).also { heldCache = it }
            val file = commitSpan(cache, "kept")

            assertUnder(file, File(context.cacheDir, "mlib"))
            val occupancy = CacheProvider.occupancy(context, dispatcher)
            assertTrue(occupancy.fellBack)
            assertEquals("Internal storage", occupancy.volumeLabel)
            assertEquals("card", PlainCacheVolumeSettings(context).read())
        }

    @Test
    fun staleMlibOnAnotherPresentVolumeIsDeletedAfterOpenAndNeverTheOpenedDir() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dispatcher = probeExecutor.asCoroutineDispatcher()
            val staleMlib = tempVolume("stale")
            val staleRoot = requireNotNull(staleMlib.dir.parentFile)
            staleMlib.dir.mkdirs()
            File(staleMlib.dir, "leftover.span").writeText("leftover")
            val sibling = File(staleRoot, "sibling.txt").apply { writeText("keep me") }
            CacheProvider.volumesFor = { listOf(internalVolume(context), staleMlib) }
            // nothing chosen: opens internal, "stale" is present but unused

            val cache = CacheProvider.get(context, dispatcher).also { heldCache = it }
            CacheProvider.awaitCleanupForTest()

            assertFalse(staleMlib.dir.exists(), "the stale mlib directory should be removed")
            assertTrue(sibling.exists(), "a file next to mlib, not inside it, must survive")
            val openedFile = commitSpan(cache, "kept")
            assertUnder(openedFile, File(context.cacheDir, "mlib"))
        }

    @Test
    fun aChosenVolumeWhoseCacheFailsToInitialiseFallsBackToInternalWithoutDeletingIt() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dispatcher = probeExecutor.asCoroutineDispatcher()
            val readOnlyRoot = Files.createTempDirectory("cache-volume-readonly-").toFile()
            readOnlyRoot.setWritable(false)
            val broken = CacheVolume("broken", "broken", File(readOnlyRoot, "mlib"), FAKE_FREE_BYTES, removable = true)
            CacheProvider.volumesFor = { listOf(internalVolume(context), broken) }
            PlainCacheVolumeSettings(context).write("broken")

            try {
                val cache = CacheProvider.get(context, dispatcher).also { heldCache = it }
                val file = commitSpan(cache, "kept")

                assertUnder(file, File(context.cacheDir, "mlib"))
                val occupancy = CacheProvider.occupancy(context, dispatcher)
                assertTrue(occupancy.fellBack)
                assertEquals("Internal storage", occupancy.volumeLabel)
            } finally {
                readOnlyRoot.setWritable(true)
            }
        }

    @Test
    fun aStoredBudgetAboveTheCapIsClampedToTheLargestLadderStepAndPrefsKeepTheOriginal() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dispatcher = probeExecutor.asCoroutineDispatcher()
            // Nothing free: the cap floors at MIN_CACHE_BYTES, so the ladder is just that one step.
            CacheProvider.volumesFor = { listOf(internalVolume(context, freeBytes = 0)) }
            PlainCacheBudgetSettings(context).write(CACHE_MAX_BYTES)

            CacheProvider.get(context, dispatcher).also { heldCache = it }
            val occupancy = CacheProvider.occupancy(context, dispatcher)

            assertEquals(MIN_CACHE_BYTES, occupancy.budgetBytes)
            assertEquals(CACHE_MAX_BYTES, PlainCacheBudgetSettings(context).read())
        }

    private fun occupancyOn(volume: CacheVolume, heldBytes: Long, budgetBytes: Long, capBytes: Long) =
        CacheOccupancy(
            heldBytes = heldBytes,
            budgetBytes = budgetBytes,
            volumeLabel = volume.label,
            fellBack = false,
            capBytes = capBytes,
        )

    private fun assertUnder(file: File, dir: File) =
        assertTrue(file.absolutePath.startsWith(dir.absolutePath + File.separator), "$file is not under $dir")

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
