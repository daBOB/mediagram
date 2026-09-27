package playback

/**
 * Caps how often a raw `CacheWriter` progress callback becomes a state
 * update. media3 calls back roughly once per internal read (tens of
 * kilobytes), hundreds of times a second on a fast LAN — far more than a
 * progress bar needs to redraw, and each one means allocating and emitting
 * a new [FilmPreloadState.Running].
 *
 * [clock] is a constructor parameter, not [System.currentTimeMillis]
 * called directly, so a test can drive it without a real sleep.
 */
class ProgressThrottle(
    private val minIntervalMs: Long = 250L,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** `null` for "never yet" — a plain `Long` sentinel would risk overflow on the very first, unbounded subtraction. */
    private var lastEmittedAt: Long? = null

    /** Whether enough time has passed since the last `true` answer to emit again — always `true` the first time asked. */
    fun shouldEmit(): Boolean {
        val now = clock()
        val last = lastEmittedAt
        if (last != null && now - last < minIntervalMs) return false
        lastEmittedAt = now
        return true
    }
}
