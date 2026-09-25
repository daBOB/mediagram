package playback

/**
 * How much of the cache's own budget preloading is allowed to reach for.
 *
 * Left short of the whole budget on purpose: the title actually playing
 * writes into the same cache as it goes, and a preload that filled every
 * last byte of the budget would be the thing that evicts it.
 */
private const val PRELOAD_BUDGET_FRACTION = 0.75

/**
 * Whether a candidate episode can be taken into the cache without
 * crowding out the title actually playing.
 *
 * [currentBytes] is that title's own whole size, reserved in full even
 * though only part of it may be on disk yet — a scrub back into an
 * already-played stretch has to still find it there. [heldBytes] is
 * everything else already cached: other titles, and earlier preloads.
 * Together with [candidateBytes] they must leave the cache under
 * [PRELOAD_BUDGET_FRACTION] of [budgetBytes], or the candidate is skipped
 * rather than risking the evictor reaching for what is playing.
 */
fun fitsInPreloadBudget(heldBytes: Long, currentBytes: Long, candidateBytes: Long, budgetBytes: Long): Boolean =
    heldBytes + currentBytes + candidateBytes <= (budgetBytes * PRELOAD_BUDGET_FRACTION).toLong()
