// media3 marks its extension surface @UnstableApi and may change it in any
// minor release; see CacheProvider for why the version is pinned rather
// than floored, and why this is androidx's opt-in and not Kotlin's.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.content.Context
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * What a caller needs to know about which sets are held — narrow enough
 * for a test to fake without a real disk cache or `Context` behind it; see
 * [HeldSets] for the real answer.
 */
interface HeldSetsQuery {
    /** `false` for a total of zero or less — nothing to compare a span against, and never actually true. */
    suspend fun isHeld(setId: String, totalBytes: Long): Boolean

    /** The held ones among `sets` (setId to its own total bytes). */
    suspend fun heldIds(sets: List<Pair<String, Long>>): Set<String>

    companion object {
        /** Nothing is ever held — a constructor default for a caller that does not care, e.g. a test exercising something else entirely. */
        val Noop: HeldSetsQuery = object : HeldSetsQuery {
            override suspend fun isHeld(setId: String, totalBytes: Long) = false
            override suspend fun heldIds(sets: List<Pair<String, Long>>): Set<String> = emptySet()
        }
    }
}

/**
 * Whether a set plays with no network at all.
 *
 * Answered from the disk cache alone: [androidx.media3.datasource.cache.Cache.isCached]
 * over the whole of a set's own span already knows, under the same key
 * playback itself reads through ([setUri]'s string form — no custom
 * [androidx.media3.datasource.cache.CacheKeyFactory] is ever set, so the
 * default is the URI). Nothing here asks Telegram anything.
 *
 * A plain class, not Hilt-injected — this module carries no Hilt plugin of
 * its own (see `CacheBudgetSettings`, the same shape); `player.di.PlaybackModule`
 * builds the one instance the app uses, bound to [HeldSetsQuery].
 */
class HeldSets(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.IO,
) : HeldSetsQuery {
    override suspend fun isHeld(setId: String, totalBytes: Long): Boolean {
        if (totalBytes <= 0) return false
        return withContext(dispatcher) {
            CacheProvider.get(context, dispatcher).isCached(setUri(setId).toString(), 0, totalBytes)
        }
    }

    /**
     * Scanned off-main in one pass — a shelf asks this once rather than
     * once per card, the same reasoning [CacheProvider.get] itself is
     * built on.
     */
    override suspend fun heldIds(sets: List<Pair<String, Long>>): Set<String> = withContext(dispatcher) {
        val cache = CacheProvider.get(context, dispatcher)
        sets.asSequence()
            .filter { (_, totalBytes) -> totalBytes > 0 }
            .filter { (setId, totalBytes) -> cache.isCached(setUri(setId).toString(), 0, totalBytes) }
            .mapTo(mutableSetOf()) { (setId, _) -> setId }
    }
}
