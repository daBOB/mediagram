package playback

/**
 * How much free space a cap leaves alone on the volume it caps. A cache
 * that fills a volume completely takes the rest of that disk down with it
 * — the phone's own storage or a card something else is also using.
 */
private const val FREE_SPACE_RESERVE_BYTES = 1L * 1024 * 1024 * 1024 // 1 GiB

/**
 * Where the cache opens, resolved from what a viewer chose and what is
 * actually present. [fellBack] is `true` only when a choice was recorded
 * and could not be honoured — never for a viewer who has never chosen,
 * whose default is internal storage on its own terms.
 */
data class CacheLocation(
    val volume: CacheVolume,
    val fellBack: Boolean,
)

/**
 * Resolves [chosenId] against [volumes]. A `null` id — nothing ever chosen
 * — opens internal storage without that counting as a fallback: there was
 * no choice to fail. A chosen id absent from [volumes] (the card was
 * ejected, or a reformat gave it a new one) also opens internal storage,
 * but as a fallback the caller reports so the choice itself is left alone.
 */
fun resolveCacheLocation(volumes: List<CacheVolume>, chosenId: String?): CacheLocation {
    val internal = volumes.first { it.id == INTERNAL_VOLUME_ID }
    if (chosenId == null) return CacheLocation(internal, fellBack = false)
    val chosen = volumes.firstOrNull { it.id == chosenId }
    return if (chosen != null) CacheLocation(chosen, fellBack = false) else CacheLocation(internal, fellBack = true)
}

/**
 * What [volume] may grow the cache to: its free space plus what the cache
 * already holds there — space it may keep using, not space it must give
 * back — less [FREE_SPACE_RESERVE_BYTES], and never below
 * [MIN_CACHE_BYTES] so the ladder always has a floor to offer even on a
 * volume with nothing to spare.
 */
fun budgetCap(volume: CacheVolume, heldBytes: Long): Long =
    maxOf(MIN_CACHE_BYTES, volume.freeBytes + heldBytes - FREE_SPACE_RESERVE_BYTES)

/**
 * The sizes offered for [cap]: doubling from [MIN_CACHE_BYTES], stopping at
 * the largest step at or below it. Always at least one entry — the floor
 * itself — so a volume too small for even that still offers something to
 * pick.
 */
fun budgetLadder(cap: Long): List<Long> =
    buildList {
        add(MIN_CACHE_BYTES)
        var next = MIN_CACHE_BYTES * 2
        while (next <= cap) {
            add(next)
            next *= 2
        }
    }
