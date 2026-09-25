package player

import model.MediaSet

/**
 * What "this show" means when a choice is remembered — a port of the web's
 * `scopeOf` (`preference-scope.js`), same prefixes, so a speed chosen on
 * one episode applies to the whole show it belongs to rather than to the
 * single set it was set on.
 *
 * Three answers, most precise first:
 * - **`key:<posterKey>`**, TMDB-backed and so surviving a re-rip or a
 *   rename. Android's [MediaSet.posterKey] is the web's `showKey` — see
 *   `web/public/lib/routes.ts`, where a poster key is built the same way
 *   for both a set and the show it belongs to.
 * - **`show:<show>`**, the name the shelves group by. Two unrelated shows
 *   sharing a name would share a preference, which is a wrong subtitle
 *   size and not a wrong anything else.
 * - **`set:<setId>`**, for a one-off with neither: a film with no TMDB id
 *   and no series.
 *
 * Prefixed so the three namespaces cannot collide — a course called
 * `tmdb-tv-1399` would otherwise be filed with whatever that key names.
 */
fun scopeOf(set: MediaSet?): String? {
    if (set == null) return null
    text(set.posterKey)?.let { return "key:$it" }
    text(set.show)?.let { return "show:$it" }
    return text(set.setId)?.let { "set:$it" }
}

/** A usable string, or nothing. Whitespace is not a name. */
private fun text(value: String?): String? = value?.trim()?.takeIf { it.isNotEmpty() }
