package catalog

/**
 * A day-seeded deterministic generator — a Kotlin port of the web's
 * `editorial-picks.js` `seededRandom` (mulberry32), so the two surfaces
 * pick the same cover and quote for the same day without exchanging a seed.
 *
 * `web/test/fixtures/editorial-picks/` pins the exact sequence a seed
 * produces; a change here that only passes this module's own tests, not
 * those fixtures, does not belong — the web is authoritative.
 */

/** One day in milliseconds, the unit `dayOf` and every window here counts in. */
const val DAY_MS = 86_400_000L

/** Which day `now` falls on, counted from the epoch; the rotation's seed. */
fun dayOf(now: Long): Long = now / DAY_MS

/**
 * A source in `[0, 1)`. `UInt` throughout so every operation wraps at 2^32
 * the way JavaScript's `>>> 0` and `Math.imul` do — the bit pattern a
 * multiplication or shift produces is the same whether the 32 bits are read
 * as signed or unsigned, so this reproduces the web's sequence exactly
 * without a 64-bit intermediate ever needing to be masked by hand.
 */
fun seededRandom(seed: Long): () -> Double {
    var a: UInt = seed.toUInt()
    return {
        a += 0x6d2b79f5u
        var t = a
        t = (t xor (t shr 15)) * (t or 1u)
        t = t xor (t + ((t xor (t shr 7)) * (t or 61u)))
        (t xor (t shr 14)).toLong().toDouble() / 4_294_967_296.0
    }
}
