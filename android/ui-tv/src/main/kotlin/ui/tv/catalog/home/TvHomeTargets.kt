package ui.tv.catalog.home

/** Which band of the magazine layout a stop belongs to, in the order they draw down the page. */
internal enum class TvHomeSection { COVER, FEATURES, CONTINUE, RECENT, SERIES, COURSES }

/** One stop on the page: which section, and which of its own stops in order. */
internal data class TvHomeTarget(val section: TvHomeSection, val stop: Int)

/**
 * Where Home's arrival focus — on first appearing, and on every restore key
 * a title or a collection opened from here hands back — lands. Pure, so the
 * restore rule can be proven without composing anything: [sections] is every
 * band in page order paired with its own stops' keys (the cover's own films'
 * ids; a resume card's or a poster's set id; a collection's key); an empty
 * key list means that band drew nothing, the same "not on the page" a
 * caller already used to skip drawing it at all.
 *
 * A [restoreKey] found in some band's own keys always wins, wherever it
 * sits — the cover rotates through five films and a `Back` from the third
 * one's title page must find that film again, not always the first. With no
 * match (`restoreKey` is `null`, names nothing here, or named a title this
 * band no longer carries) the default is the first band with any stops at
 * all, at its own first one — `null` only when every band is empty, which a
 * caller never actually draws a page for.
 */
internal fun homeTargetOf(
    sections: List<Pair<TvHomeSection, List<String>>>,
    restoreKey: String?,
): TvHomeTarget? {
    if (restoreKey != null) {
        for ((section, keys) in sections) {
            val stop = keys.indexOf(restoreKey)
            if (stop >= 0) return TvHomeTarget(section, stop)
        }
    }
    val first = sections.firstOrNull { (_, keys) -> keys.isNotEmpty() } ?: return null
    return TvHomeTarget(first.first, 0)
}
