package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import catalog.CatalogUiState
import catalog.VersionRow
import catalog.filmDetailSections
import catalog.versionRowsOf
import designsystem.Spacing
import designsystem.TvType
import model.MediaSet
import model.WatchSnapshot
import ui.common.catalog.VersionAsk
import ui.tv.setup.TvDialog

/** A Versions row's tag, so a test can find the rows without leaning on their wording. */
internal const val TvVersionRowTag = "tv-version-row"

/**
 * A film held more than once, one row per copy in version order — the web's
 * `versionRows`: its label, its technical line, where this viewer is in it,
 * the offline badge when held, and its own Play, which plays that copy
 * without asking. [first] goes on the first row's Play, where the remote
 * lands.
 */
@Composable
internal fun TvVersionRows(
    rows: List<VersionRow>,
    onPlay: (MediaSet) -> Unit,
    modifier: Modifier = Modifier,
    first: FocusRequester? = null,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        rows.forEachIndexed { index, row ->
            Row(
                modifier = Modifier.fillMaxWidth().testTag(TvVersionRowTag),
                horizontalArrangement = Arrangement.spacedBy(Spacing.large),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TvSpreadPill(
                    text = "▶ ${row.action}",
                    onClick = { onPlay(row.set) },
                    description = "${row.action} ${row.label}",
                    modifier = Modifier.width(PlayWidth).let { pill -> first?.takeIf { index == 0 }?.let { pill.focusRequester(it) } ?: pill },
                )
                Column(Modifier.weight(1f)) {
                    Text(row.label, style = TvType.bodyLarge)
                    if (row.technical.isNotEmpty()) TvQuietLine(row.technical)
                    row.state?.let { TvQuietLine(it) }
                    if (row.held) TvOfflineBadge(modifier = Modifier.padding(top = Spacing.extraSmall))
                }
            }
        }
    }
}

/**
 * The question asked when a card that plays straight away stands for a film
 * held more than once: the same rows as its page, the first holding the
 * remote; Cancel and Back play nothing. The web asks in a centred dialog and
 * the phone in a bottom sheet; the television asks in its own pick-one
 * dialog — the one deliberate difference between the surfaces.
 */
@Composable
internal fun TvVersionChooser(
    ask: VersionAsk,
    catalogState: CatalogUiState,
) {
    val question = ask.question ?: return
    val ready = catalogState as? CatalogUiState.Ready
    val watch = ready?.watch ?: WatchSnapshot.Empty
    val rows = remember(question, watch) { versionRowsOf(question.film, watch, ready?.heldIds.orEmpty()) }
    val first = remember { FocusRequester() }
    TvDialog(
        title = question.film.title,
        body = "",
        onDismissRequest = ask::cancel,
        initialFocus = first,
        content = { TvVersionRows(rows, ask::choose, Modifier.padding(top = Spacing.medium), first) },
    ) { Button(onClick = ask::cancel) { Text("Cancel", style = TvType.labelLarge) } }
}

/** Wide enough for "▶ Resume", so the words beside every row start in one column. */
private val PlayWidth = 180.dp

/** A film page's Details tab: the file's facts, or each copy's under its label for a film held more than once. */
@Composable
internal fun TvFilmDetails(set: MediaSet) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        for ((label, rows) in filmDetailSections(set)) {
            label?.let { TvSectionHeading(it) }
            TvFactSheet(rows)
        }
    }
}
