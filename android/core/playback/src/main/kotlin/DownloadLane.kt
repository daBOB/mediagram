package playback

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The one write slot the series and film preloaders share, so their writes
 * into the same disk cache — and the same home cache server, over the same
 * LAN write path — never race each other for bandwidth. Both preloaders are
 * handed the same instance (see `PlaybackModule`); each `SeriesPreloader`
 * test that does not pass one gets its own, private lane instead, since
 * nothing there writes concurrently with anything else.
 *
 * A plain `Mutex`, not a semaphore or a queue of its own: the ordering
 * between the two preloaders is not a rule this lane enforces, only mutual
 * exclusion — whichever one asks first while the other is idle writes
 * first, and a `FilmPreloader` that must interrupt its own write to let
 * playback through releases the lock the same way finishing normally would.
 */
class DownloadLane {
    private val mutex = Mutex()

    suspend fun <T> withLane(block: suspend () -> T): T = mutex.withLock { block() }
}
