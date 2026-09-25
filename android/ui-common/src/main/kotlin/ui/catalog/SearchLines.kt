package ui.catalog

import catalog.humanDuration
import model.Kind
import model.MediaSet
import model.episodeLabel
import model.humanSize

/**
 * The words a search hit is drawn with, and what the search screen says
 * under its field — one copy for the phone's and the television's search,
 * so a viewer who uses both reads the same row on each.
 */

/**
 * Where a hit sits, as a person would say it — ported from `locationOf` in
 * `search-view.js`. A film says the year it is from; an episode says its
 * show and its number; a lesson or a document says its course and the
 * folder it sits in, the folder path over the generated chapter label for
 * the same reason the shelves show it.
 */
fun locationOf(set: MediaSet): String? = when (set.kind) {
    Kind.EPISODE -> listOfNotNull(set.show, episodeLabel(set).takeIf(String::isNotEmpty))
        .joinToString(" · ")
        .takeIf(String::isNotEmpty)

    Kind.TUTORIAL, Kind.DOCUMENT -> listOfNotNull(
        set.show,
        set.path?.takeIf(String::isNotBlank) ?: set.chapter?.takeIf(String::isNotBlank),
    ).joinToString(" · ").takeIf(String::isNotEmpty)

    Kind.MOVIE -> set.year?.takeIf { it > 0 }?.toString()
}

/**
 * `mkv · hevc · eac3 · 1h 53m · 14 GB` — shorter than `technicalLine`,
 * which stays as it is for a title's own page; a search hit does not need
 * the quality, HDR, part count or bitrate a browsing card never asked for
 * either. Mirrors `codecLine` in the web's `format.js`.
 */
fun searchMetaLineOf(set: MediaSet): String = listOfNotNull(
    set.container.takeIf(String::isNotEmpty),
    set.vcodec?.takeIf(String::isNotEmpty),
    set.acodec?.takeIf(String::isNotEmpty),
    humanDuration(set.durationSecs),
    set.totalBytes.takeIf { it > 0 }?.let(::humanSize),
).joinToString(" · ")

/** `1 result`, `12 titles` — mirrors `countOf` in the web's `format.js`. */
fun countOf(count: Int, noun: String): String = "$count ${if (count == 1) noun else "${noun}s"}"

/**
 * Whether a hit can be opened. A document cannot — the player would be
 * handed a PDF — so a search row shows it and does not open it, the same
 * rule a course's own rows already follow.
 */
fun isPlayable(set: MediaSet): Boolean = set.kind != Kind.DOCUMENT

/**
 * What the search screen says under the field — pure, so the rule is
 * tested without a `Composable`. A restore can settle on a real answer
 * before the catalog itself has finished loading; joining against it then
 * would read as "nothing found" rather than "not yet asked", so
 * `catalogReady` is checked first, the same order a genre page uses.
 */
enum class SearchResultsView { LOADING, EMPTY, ROWS }

fun searchResultsView(catalogReady: Boolean, rowsEmpty: Boolean): SearchResultsView = when {
    !catalogReady -> SearchResultsView.LOADING
    rowsEmpty -> SearchResultsView.EMPTY
    else -> SearchResultsView.ROWS
}
