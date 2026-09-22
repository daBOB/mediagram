// Same reason as AdjustableLruEvictor.kt: CacheEvictor, Cache and CacheSpan
// are all @UnstableApi.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheSpan
import androidx.media3.datasource.cache.ContentMetadata
import androidx.media3.datasource.cache.ContentMetadataMutations
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * A [Cache] whose only real behaviour is `removeSpan`: it records the
 * removal and calls back into the evictor exactly as `SimpleCache` does —
 * that callback is what lets [AdjustableLruEvictor]'s eviction loop see
 * its held bytes actually shrink. Every other member is unused by the
 * evictor under test and left unimplemented.
 */
private class FakeCache(private val evictor: AdjustableLruEvictor) : Cache {
    val removed = mutableListOf<CacheSpan>()

    override fun removeSpan(span: CacheSpan) {
        removed.add(span)
        evictor.onSpanRemoved(this, span)
    }

    override fun getUid(): Long = 0
    override fun release() = Unit
    override fun addListener(key: String, listener: Cache.Listener) = throw UnsupportedOperationException()
    override fun removeListener(key: String, listener: Cache.Listener) = Unit
    override fun getCachedSpans(key: String) = throw UnsupportedOperationException()
    override fun getKeys() = throw UnsupportedOperationException()
    override fun getCacheSpace(): Long = throw UnsupportedOperationException()
    override fun startReadWrite(key: String, position: Long, length: Long) = throw UnsupportedOperationException()
    override fun startReadWriteNonBlocking(key: String, position: Long, length: Long) = throw UnsupportedOperationException()
    override fun startFile(key: String, position: Long, length: Long): File = throw UnsupportedOperationException()
    override fun commitFile(file: File, length: Long) = Unit
    override fun releaseHoleSpan(holeSpan: CacheSpan) = Unit
    override fun removeResource(key: String) = Unit
    override fun isCached(key: String, position: Long, length: Long): Boolean = throw UnsupportedOperationException()
    override fun getCachedLength(key: String, position: Long, length: Long): Long = throw UnsupportedOperationException()
    override fun getCachedBytes(key: String, position: Long, length: Long): Long = throw UnsupportedOperationException()
    override fun applyContentMetadataMutations(key: String, mutations: ContentMetadataMutations) = Unit
    override fun getContentMetadata(key: String): ContentMetadata = throw UnsupportedOperationException()
}

/** A span of [length] bytes last touched at [lastTouchTimestamp], for a key that never needs to resolve to a real file. */
private fun span(key: String, length: Long, lastTouchTimestamp: Long): CacheSpan =
    CacheSpan(key, 0L, length, lastTouchTimestamp, File("$key.span"))

class AdjustableLruEvictorTest {

    @Test
    fun addingBeyondBudgetEvictsTheLeastRecentlyUsedSpanFirst() {
        val evictor = AdjustableLruEvictor(initialBudgetBytes = 100)
        val cache = FakeCache(evictor)
        val oldest = span("a", length = 60, lastTouchTimestamp = 1)
        val newer = span("b", length = 60, lastTouchTimestamp = 2)

        evictor.onSpanAdded(cache, oldest)
        evictor.onSpanAdded(cache, newer)

        assertEquals(listOf(oldest), cache.removed)
    }

    @Test
    fun setBudgetShrinksTheCacheEvictingLeastRecentlyUsedFirst() {
        val evictor = AdjustableLruEvictor(initialBudgetBytes = 200)
        val cache = FakeCache(evictor)
        val oldest = span("a", length = 50, lastTouchTimestamp = 1)
        val middle = span("b", length = 50, lastTouchTimestamp = 2)
        val newest = span("c", length = 50, lastTouchTimestamp = 3)
        evictor.onSpanAdded(cache, oldest)
        evictor.onSpanAdded(cache, middle)
        evictor.onSpanAdded(cache, newest)
        assertTrue(cache.removed.isEmpty(), "nothing should be evicted while under the original budget")

        evictor.setBudget(60, cache)

        assertEquals(listOf(oldest, middle), cache.removed)
        assertEquals(60L, evictor.budgetBytes)
    }

    @Test
    fun startingAFileEvictsSpaceForItBeforeItIsWritten() {
        val evictor = AdjustableLruEvictor(initialBudgetBytes = 100)
        val cache = FakeCache(evictor)
        val existing = span("a", length = 80, lastTouchTimestamp = 1)
        evictor.onSpanAdded(cache, existing)

        evictor.onStartFile(cache, key = "b", position = 0, length = 50)

        assertEquals(listOf(existing), cache.removed)
    }

    @Test
    fun touchingASpanMovesItToTheMostRecentlyUsedEnd() {
        val evictor = AdjustableLruEvictor(initialBudgetBytes = 100)
        val cache = FakeCache(evictor)
        val a = span("a", length = 40, lastTouchTimestamp = 1)
        val b = span("b", length = 40, lastTouchTimestamp = 2)
        evictor.onSpanAdded(cache, a)
        evictor.onSpanAdded(cache, b)

        val touchedA = span("a", length = 40, lastTouchTimestamp = 3)
        evictor.onSpanTouched(cache, a, touchedA)

        // b is now the least recently used; a third span pushing the cache
        // over budget should evict b, not the just-touched a.
        val c = span("c", length = 40, lastTouchTimestamp = 4)
        evictor.onSpanAdded(cache, c)

        assertEquals(listOf(b), cache.removed)
    }

    @Test
    fun requiresTouchesSoLastTouchTimestampStaysAccurate() {
        val evictor = AdjustableLruEvictor(initialBudgetBytes = 100)

        assertTrue(evictor.requiresCacheSpanTouches())
    }
}
