// Same reason as CacheProvider.kt's header: SimpleCache and Cache are both
// @UnstableApi.
@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package playback

import android.util.Log
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.SimpleCache
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/** What [openCache] built: the cache itself and the volume it actually landed on. */
internal data class OpenedCache(
    val cache: SimpleCache,
    val evictor: AdjustableLruEvictor,
    val volume: CacheVolume,
    val fellBack: Boolean,
    val capBytes: Long,
)

/**
 * Resolves [chosenId] against [volumes], opens a real cache there, and
 * falls back to internal storage if that volume's cache fails to
 * initialise — the same outcome as a missing volume.
 *
 * The budget passed to [AdjustableLruEvictor] at construction is a
 * placeholder that never evicts, because the cap depends on what the cache
 * already holds there, which is only known once it has replayed its index
 * on open; the real budget is applied immediately after, directly on the
 * evictor rather than through [CacheProvider.setBudget], which would
 * overwrite the stored choice with the clamp.
 */
internal fun openCache(
    volumes: List<CacheVolume>,
    chosenId: String?,
    storedBudgetBytes: Long,
    databaseProvider: DatabaseProvider,
): OpenedCache {
    val location = resolveCacheLocation(volumes, chosenId)
    val internal = volumes.first { it.id == INTERNAL_VOLUME_ID }
    var (cache, cacheEvictor) = openAt(location.volume, databaseProvider)
    var volume = location.volume
    var fellBack = location.fellBack
    val failure = checkedInitializationFailure(cache)
    if (failure != null && volume.id != internal.id) {
        Log.w("CacheProvider", "cache failed to initialise on ${volume.label}, falling back to internal storage", failure)
        cache.release()
        val fallback = openAt(internal, databaseProvider)
        cache = fallback.first
        cacheEvictor = fallback.second
        volume = internal
        fellBack = true
    }
    val capBytes = budgetCap(volume, cache.cacheSpace)
    val appliedBudget = if (storedBudgetBytes > capBytes) budgetLadder(capBytes).last() else storedBudgetBytes
    cacheEvictor.setBudget(appliedBudget, cache)
    return OpenedCache(cache, cacheEvictor, volume, fellBack, capBytes)
}

private fun openAt(
    volume: CacheVolume,
    databaseProvider: DatabaseProvider,
): Pair<SimpleCache, AdjustableLruEvictor> {
    // Never evicts on its own: onSpanAdded fires once per span already on
    // disk while the index replays below, and a real budget here could
    // delete content this same open is about to keep.
    val newEvictor = AdjustableLruEvictor(Long.MAX_VALUE)
    return SimpleCache(volume.dir, newEvictor, databaseProvider) to newEvictor
}

private fun checkedInitializationFailure(cache: SimpleCache): Cache.CacheException? =
    try {
        cache.checkInitialization()
        null
    } catch (e: Cache.CacheException) {
        e
    }

/**
 * Removes every other present volume's `mlib` directory, never
 * [openedVolume]'s, on a coroutine launched onto [dispatcher] — scheduled
 * by [CacheProvider.get] but never awaited by it, so a card full of
 * another title's leftovers never delays the first frame.
 */
internal fun scheduleStaleVolumeCleanup(
    dispatcher: CoroutineDispatcher,
    databaseProvider: DatabaseProvider,
    volumes: List<CacheVolume>,
    openedVolume: CacheVolume,
): Job =
    CoroutineScope(dispatcher).launch {
        for (volume in volumes) {
            if (volume.dir == openedVolume.dir) continue
            try {
                SimpleCache.delete(volume.dir, databaseProvider)
            } catch (
                @Suppress("TooGenericExceptionCaught") e: Exception,
            ) {
                Log.w("CacheProvider", "could not delete the stale cache at ${volume.label}", e)
            }
        }
    }
