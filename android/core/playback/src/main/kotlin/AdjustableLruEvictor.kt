// See CacheProvider.kt's header for why @UnstableApi is opted into at file
// scope here too: CacheEvictor, Cache and CacheSpan are all marked with it.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import androidx.media3.common.C
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheEvictor
import androidx.media3.datasource.cache.CacheSpan
import java.util.TreeSet

/**
 * [androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor] with one
 * difference: that evictor's budget is `final`, set once at construction,
 * and a Settings screen needs to lower or raise it while the cache is
 * already open — possibly already holding more than the new budget allows.
 * This copies its bookkeeping exactly (a `TreeSet` ordered by
 * [CacheSpan.lastTouchTimestamp], so the least recently touched span is
 * always [TreeSet.first]) and adds [setBudget] as a third place eviction
 * can start from, alongside the stock evictor's two ([onSpanAdded],
 * [onStartFile]).
 *
 * [budgetBytes] is `@Volatile` because [setBudget] is called from whatever
 * thread a Settings change lands on, while every other member here only
 * ever runs on the single thread `SimpleCache` confines its listener calls
 * to — that thread needs to see a budget change immediately, not stale.
 */
class AdjustableLruEvictor(initialBudgetBytes: Long) : CacheEvictor {

    @Volatile
    var budgetBytes: Long = initialBudgetBytes
        private set

    private val leastRecentlyUsed = TreeSet<CacheSpan> { lhs, rhs ->
        val delta = lhs.lastTouchTimestamp - rhs.lastTouchTimestamp
        when {
            delta == 0L -> lhs.compareTo(rhs)
            delta < 0L -> -1
            else -> 1
        }
    }

    private var heldBytes = 0L

    override fun requiresCacheSpanTouches(): Boolean = true

    override fun onCacheInitialized() {
        // SimpleCache replays onSpanAdded for every span already on disk
        // before this fires, so leastRecentlyUsed and heldBytes are already
        // correct by the time it does.
    }

    override fun onStartFile(cache: Cache, key: String, position: Long, length: Long) {
        if (length != C.LENGTH_UNSET.toLong()) {
            evict(cache, length)
        }
    }

    override fun onSpanAdded(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.add(span)
        heldBytes += span.length
        evict(cache, 0)
    }

    override fun onSpanRemoved(cache: Cache, span: CacheSpan) {
        leastRecentlyUsed.remove(span)
        heldBytes -= span.length
    }

    override fun onSpanTouched(cache: Cache, oldSpan: CacheSpan, newSpan: CacheSpan) {
        onSpanRemoved(cache, oldSpan)
        onSpanAdded(cache, newSpan)
    }

    /**
     * Applies a new budget immediately, evicting the least recently used
     * spans until [cache]'s held bytes fit under it. The Settings row that
     * calls this shows "Held" dropping right away, not the next time
     * something is added to the cache.
     */
    fun setBudget(bytes: Long, cache: Cache) {
        budgetBytes = bytes
        evict(cache, 0)
    }

    /**
     * `cache.removeSpan` calls back into [onSpanRemoved] synchronously
     * before returning (that is how `SimpleCache` keeps every listener,
     * this evictor included, in step with its index), which is what lets
     * this loop converge on [heldBytes] actually shrinking rather than
     * looping forever.
     */
    private fun evict(cache: Cache, requiredBytes: Long) {
        while (heldBytes + requiredBytes > budgetBytes && leastRecentlyUsed.isNotEmpty()) {
            cache.removeSpan(leastRecentlyUsed.first())
        }
    }
}
