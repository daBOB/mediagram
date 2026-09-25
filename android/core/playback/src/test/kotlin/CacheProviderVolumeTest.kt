@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.SimpleCache
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.nio.file.Files
import java.util.concurrent.Executors
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Where [CacheProvider.get] actually opens: a present chosen volume, a
 * fallback to internal when it is absent or fails to initialise, the
 * stale-volume sweep that follows a successful open, and the budget clamp
 * against a volume's cap. [CacheProviderTest] keeps the no-choice-recorded
 * locking behaviour this file's fixtures were split out of.
 */
@RunWith(RobolectricTestRunner::class)
class CacheProviderVolumeTest {
    private val probeExecutor = Executors.newSingleThreadExecutor()
    private var heldCache: SimpleCache? = null

    @Before
    fun resetTheSharedCache() {
        CacheProvider.resetForTest()
        val context = ApplicationProvider.getApplicationContext<Context>()
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
            val databaseProvider = StandaloneDatabaseProvider(context)
            // A real, previously-working cache, with a real span and the
            // uid `SimpleCache.delete` needs to also drop the index's
            // database rows for — not a leftover file with no uid, which
            // `delete` would only plain-`File.delete()` and never exercise
            // that path at all.
            val seed = SimpleCache(staleMlib.dir, AdjustableLruEvictor(Long.MAX_VALUE), databaseProvider)
            commitSpan(seed, "was-cached")
            seed.release()
            val sibling = File(staleRoot, "sibling.txt").apply { writeText("keep me") }
            CacheProvider.volumesFor = { listOf(internalVolume(context), staleMlib) }
            // nothing chosen: opens internal, "stale" is present but unused

            val cache = CacheProvider.get(context, dispatcher).also { heldCache = it }
            CacheProvider.awaitCleanupForTest()

            assertFalse(staleMlib.dir.exists(), "the stale mlib directory should be removed")
            assertTrue(sibling.exists(), "a file next to mlib, not inside it, must survive")
            val openedFile = commitSpan(cache, "kept")
            assertUnder(openedFile, File(context.cacheDir, "mlib"))
            // Reopening from scratch must not resurrect the old content —
            // proof the index was actually dropped, not just the directory.
            val reopenedStale = SimpleCache(staleMlib.dir, AdjustableLruEvictor(Long.MAX_VALUE), databaseProvider)
            try {
                assertEquals(0L, reopenedStale.cacheSpace, "a swept volume's index must not survive under a fresh open")
                assertFalse(reopenedStale.isCached("was-cached", 0, MIN_CACHE_BYTES))
            } finally {
                reopenedStale.release()
            }
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
}
