@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.content.Context
import androidx.media3.database.DatabaseProvider
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What must never happen when a chosen volume fails to initialise: its own
 * existing content must not be wiped by the stale-volume sweep that
 * follows a *successful* open elsewhere ([scheduleStaleVolumeCleanup] must
 * skip the chosen volume, not only the opened one — an unreadable
 * directory can itself make `SimpleCache.delete` a no-op, which would mask
 * this exact bug in an end-to-end test), and nothing may be swept at all
 * when even the fallback fails to initialise.
 */
@RunWith(RobolectricTestRunner::class)
class CacheProviderFallbackTest {
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
    fun theSweepNeverDeletesTheChosenVolumeEvenWhenAFallbackOpenedElsewhere() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val internal = internalVolume(context)
            val card = tempVolume("card")
            val bystander = tempVolume("bystander")
            val databaseProvider = StandaloneDatabaseProvider(context)
            // Real, genuinely stale content on both: only the bystander is
            // actually fair game for the sweep.
            seedRealCache(card, databaseProvider, "card-content")
            seedRealCache(bystander, databaseProvider, "bystander-content")

            // Same shape openCache() would pass after a card-init-failure
            // fallback to internal: openedVolume=internal, chosenVolume=card.
            scheduleStaleVolumeCleanup(
                probeExecutor.asCoroutineDispatcher(),
                databaseProvider,
                volumes = listOf(internal, card, bystander),
                openedVolume = internal,
                chosenVolume = card,
            ).join()

            assertTrue(card.dir.exists(), "the chosen volume must survive even though another volume was opened")
            assertTrue(seededCacheHolds(card, databaseProvider, "card-content"), "the chosen volume's real content must survive")
            assertFalse(bystander.dir.exists(), "an unrelated, genuinely stale volume is still swept")
        }

    @Test
    fun whenTheFallbackAlsoFailsToInitialiseNothingIsSwept() =
        runTest {
            val context = ApplicationProvider.getApplicationContext<Context>()
            val dispatcher = probeExecutor.asCoroutineDispatcher()
            val brokenCardRoot = Files.createTempDirectory("cache-volume-card-").toFile().apply { setWritable(false) }
            val brokenCard = CacheVolume("card", "card", File(brokenCardRoot, "mlib"), FAKE_FREE_BYTES, removable = true)
            val brokenInternalRoot = Files.createTempDirectory("cache-volume-internal-").toFile().apply { setWritable(false) }
            val brokenInternal =
                CacheVolume(INTERNAL_VOLUME_ID, "Internal storage", File(brokenInternalRoot, "mlib"), FAKE_FREE_BYTES, removable = false)
            // An innocent, present, genuinely stale volume that a sweep would
            // otherwise be entitled to remove.
            val bystander = tempVolume("bystander")
            seedRealCache(bystander, StandaloneDatabaseProvider(context), "unrelated")
            CacheProvider.volumesFor = { listOf(brokenInternal, brokenCard, bystander) }
            PlainCacheVolumeSettings(context).write("card")

            try {
                CacheProvider.get(context, dispatcher).also { heldCache = it }
                CacheProvider.awaitCleanupForTest()

                assertTrue(bystander.dir.exists(), "no sweep may run when the final open itself failed to initialise")
            } finally {
                brokenCardRoot.setWritable(true)
                brokenInternalRoot.setWritable(true)
            }
        }

    private fun seedRealCache(volume: CacheVolume, databaseProvider: DatabaseProvider, key: String) {
        val seed = SimpleCache(volume.dir, AdjustableLruEvictor(Long.MAX_VALUE), databaseProvider)
        commitSpan(seed, key)
        seed.release()
    }

    private fun seededCacheHolds(volume: CacheVolume, databaseProvider: DatabaseProvider, key: String): Boolean {
        val reopened = SimpleCache(volume.dir, AdjustableLruEvictor(Long.MAX_VALUE), databaseProvider)
        return try {
            reopened.isCached(key, 0, MIN_CACHE_BYTES)
        } finally {
            reopened.release()
        }
    }
}
