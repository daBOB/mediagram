// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.content.Context
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.cache.CacheWriter
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * [HeldSets] against a real [androidx.media3.datasource.cache.SimpleCache]
 * under Robolectric — a mock `Cache` would only prove this class calls
 * `isCached`, not that the key it asks under is the one playback and a
 * preload actually write under.
 */
@RunWith(RobolectricTestRunner::class)
class HeldSetsTest {

    @Before
    fun resetTheSharedCache() {
        CacheProvider.resetForTest()
    }

    @Test
    fun aSetWithNothingCachedIsNotHeld() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertFalse(HeldSets(context).isHeld("s1", totalBytes = 1_000))
    }

    @Test
    fun aZeroOrNegativeTotalIsNeverHeld() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertFalse(HeldSets(context).isHeld("s1", totalBytes = 0))
    }

    /** Written the same way [CacheDataSourceWriter] does — the same factory, the same key. */
    @Test
    fun aSetFullyWrittenThroughTheSameCacheDataSourceIsHeld() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = cacheDataSourceFactory(context, PlaybackCounters()) { FakeCore(totalSize = 1_000) }
        CacheWriter(factory.createDataSource(), DataSpec(setUri("s1")), null, null).cache()

        assertTrue(HeldSets(context).isHeld("s1", totalBytes = 1_000))
    }

    @Test
    fun heldIdsReturnsOnlyTheSetsActuallyOnDisk() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val factory = cacheDataSourceFactory(context, PlaybackCounters()) { FakeCore(totalSize = 500) }
        CacheWriter(factory.createDataSource(), DataSpec(setUri("held")), null, null).cache()

        val heldIds = HeldSets(context).heldIds(listOf("held" to 500L, "missing" to 500L))

        assertEquals(setOf("held"), heldIds)
    }
}
