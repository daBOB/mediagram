package playback

/**
 * The cache's live budget — what a `NeedsSpace` label names when it says
 * why a film does not fit, read fresh rather than assumed, since a viewer
 * can raise it in Settings while a film sits waiting. Narrow enough for a
 * test to fake without a real disk cache behind it, the same reasoning
 * [HeldSetsQuery] already gives for itself.
 */
fun interface CacheBudgetQuery {
    suspend fun currentBudgetBytes(): Long?

    companion object {
        /** Unknown — a constructor default for a caller that does not care what the budget is. */
        val Noop: CacheBudgetQuery = CacheBudgetQuery { null }
    }
}
