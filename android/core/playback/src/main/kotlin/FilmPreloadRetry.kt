package playback

/** What one write attempt inside [FilmPreloader.runItem] came to. */
internal sealed interface WriteOutcome {
    data object Completed : WriteOutcome

    /** `CacheWriter` was cancelled mid-write — [FilmPreloader] itself decides whether that means a pause or a stop. */
    data object Interrupted : WriteOutcome

    data class Failed(val cause: Exception) : WriteOutcome
}

/** How a whole film — not just one write attempt — ended, resolved once in `finally` by `FilmPreloader.settle` regardless of which branch actually set it. */
internal sealed interface ItemOutcome {
    data object Done : ItemOutcome
    data class NeedsSpace(val neededBytes: Long) : ItemOutcome
    data class Failed(val reason: String) : ItemOutcome
    data object Interrupted : ItemOutcome
}

/**
 * Delay before the next retry after [consecutiveFailures] write failures
 * with no forward progress since the last one. The core has no way to tell
 * Kotlin a Telegram `FLOOD_WAIT` apart from any other network fault (see
 * `CoreErrors.kt`), so this backs off blindly rather than reading a wait
 * time nothing here can be given — capped so a long-stuck preload is still
 * retried within half a minute, and bounded by the caller so one that never
 * recovers eventually reports [FilmPreloadState.Failed] instead of retrying
 * forever.
 */
internal fun backoffDelayMs(consecutiveFailures: Int): Long =
    (1_000L shl consecutiveFailures.coerceIn(0, 5)).coerceAtMost(30_000L)
