package ui.tv.catalog.home

import ui.tv.catalog.DeptSection
import ui.tv.catalog.SectionStop
import ui.tv.catalog.firstStopOf
import ui.tv.catalog.restoreTargetOf

/** Which band of the magazine layout a stop belongs to, in the order they draw down the page. */
internal enum class TvHomeSection { COVER, FEATURES, CONTINUE, RECENT, SERIES, COURSES }

/**
 * Where Home's arrival focus — on first appearing, and on every restore key
 * a title or a collection opened from here hands back — lands. Pure, so the
 * rule can be proven without composing anything: [sections] is every band
 * in page order with its own stops' keys; an empty list means that band drew
 * nothing.
 *
 * [restoreTargetOf]'s rule, which every department page shares: a key in
 * [lastSection] wins, else the first band down the page that carries it —
 * the cover rotates through five films and Back from the third one's title
 * page must find that film again. With no match, the first band with any
 * stops, at its first one; `null` only when every band is empty, which a
 * caller never draws a page for.
 */
internal fun homeTargetOf(
    sections: List<DeptSection<TvHomeSection>>,
    restoreKey: String?,
    lastSection: TvHomeSection? = null,
): SectionStop<TvHomeSection>? = restoreTargetOf(sections, restoreKey, lastSection) ?: firstStopOf(sections)
