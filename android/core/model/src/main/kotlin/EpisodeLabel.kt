package model

/**
 * `S1E4` for an episode, `4` for a lesson, a `4-5` range for a set spanning
 * more than one, empty when unnumbered — ported from `episodeLabel` in the
 * web's `format.js`.
 *
 * Lives in `core:model` rather than one feature module, because both
 * `feature:catalog` (a set's line on a shelf card) and `feature:player`
 * (the title line at the top of the player) need the same word for the
 * same fact, and a feature module depending on another feature module to
 * borrow a formatting function would be backwards.
 */
fun episodeLabel(set: MediaSet): String {
    val first = set.episodeFirst ?: return ""
    val last = set.episodeLast
    val number = if (last != null && last != first) "$first-$last" else "$first"
    return if (set.kind == Kind.EPISODE && set.season != null) "S${set.season}E$number" else number
}
