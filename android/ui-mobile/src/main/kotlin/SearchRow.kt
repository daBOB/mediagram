package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import catalog.SearchRow
import catalog.episodeLabel
import catalog.searchWhy
import designsystem.Spacing
import model.Kind
import model.MediaSet
import model.Progress

/**
 * One hit: title, where it sits, why it matched, what it is, and — if this
 * viewer has been partway through it — the same progress rule a shelf card
 * draws. A tap plays it directly, the same dialog a shelf card opens; a
 * search result is a set, not a link to somewhere else. Ported from
 * `search-view.js`'s row, one flat list rather than shelves: the point of
 * searching a hundred lessons named "Definition" is that the best answer is
 * first, and a heading would bury it.
 *
 * A document is the one hit this cannot open — the player would be handed a
 * PDF — so it is shown and not tapped, the same rule [ItemRow] already
 * follows for one inside a course.
 */
@Composable
internal fun SearchResultRow(row: SearchRow, progress: Progress?, watched: Boolean, onPlay: (String) -> Unit) {
    val set = row.set
    val playable = isPlayable(set)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (playable) it.clickable(role = Role.Button) { onPlay(set.setId) } else it }
            .padding(vertical = Spacing.small),
    ) {
        Text(
            text = "${if (watched) "✓ " else ""}${set.title}",
            style = MaterialTheme.typography.bodyLarge,
            color = if (playable) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (!playable) {
            Text(DOCUMENT_REASON, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        locationOf(set)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        // The reason a summary hit is worth showing at all.
        row.excerpt?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        searchWhy(row.matched)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        metaLineOf(set).takeIf(String::isNotEmpty)?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        watchedFractionOf(progress)?.let { fraction ->
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(top = Spacing.extraSmall),
            )
        }
        HorizontalDivider(modifier = Modifier.padding(top = Spacing.small))
    }
}

/**
 * Where a hit sits, as a person would say it — ported from `locationOf` in
 * `search-view.js`. A film says the year it is from; an episode says its
 * show and its number; a lesson or a document says its course and the
 * folder it sits in, the folder path over the generated chapter label for
 * the same reason the shelves show it.
 */
internal fun locationOf(set: MediaSet): String? = when (set.kind) {
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
 * `mkv · hevc · eac3 · 1h 53m · 14 GB` — shorter than [technicalLine],
 * which stays as it is for a title's own page; a search hit does not need
 * the quality, HDR, part count or bitrate a browsing card never asked for
 * either. Mirrors `codecLine` in the web's `format.js`.
 */
private fun metaLineOf(set: MediaSet): String = listOfNotNull(
    set.container.takeIf(String::isNotEmpty),
    set.vcodec?.takeIf(String::isNotEmpty),
    set.acodec?.takeIf(String::isNotEmpty),
    humanDuration(set.durationSecs),
    set.totalBytes.takeIf { it > 0 }?.let(::humanSize),
).joinToString(" · ")

/** `1 result`, `12 titles` — mirrors `countOf` in the web's `format.js`. */
internal fun countOf(count: Int, noun: String): String = "$count ${if (count == 1) noun else "${noun}s"}"

/**
 * Whether a hit can be opened. A document cannot — the player would be
 * handed a PDF — so [SearchResultRow] shows it and does not tap it, the
 * same rule [ItemRow] already follows for one inside a course.
 */
internal fun isPlayable(set: MediaSet): Boolean = set.kind != Kind.DOCUMENT

/**
 * What the search screen says under the field — pure, so the rule is
 * tested without a `Composable`. A restore can settle on a real answer
 * before the catalog itself has finished loading; joining against it then
 * would read as "nothing found" rather than "not yet asked", so
 * `catalogReady` is checked first, the same order [GenreBranch] uses.
 */
internal enum class SearchResultsView { LOADING, EMPTY, ROWS }

internal fun searchResultsView(catalogReady: Boolean, rowsEmpty: Boolean): SearchResultsView = when {
    !catalogReady -> SearchResultsView.LOADING
    rowsEmpty -> SearchResultsView.EMPTY
    else -> SearchResultsView.ROWS
}
