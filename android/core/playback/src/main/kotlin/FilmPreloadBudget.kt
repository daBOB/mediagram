package playback

/**
 * Whether a film's whole size can ever fit the cache budget.
 *
 * Not "does it fit alongside what is already held" — this cache is an LRU
 * (`AdjustableLruEvictor`), so anything already there, preloads included,
 * is free to be evicted to make room; there is no pinning. The only thing
 * that must be protected is [reservedBytes]: the title actually open in
 * the player right now, if it is a *different* film than [totalBytes]'s
 * own — reserved in full so a preload can never be what evicts the very
 * thing playing. `0` when nothing is open, or when the open title is this
 * same film (nothing to double-reserve against itself).
 *
 * `false` here is `NeedsSpace`: raising the budget is the only way
 * forward, since the film alone — not counting anything else the cache
 * might be asked to evict — still does not fit.
 */
fun fitsFilmPreloadBudget(totalBytes: Long, reservedBytes: Long, budgetBytes: Long): Boolean =
    totalBytes <= budgetBytes - reservedBytes
