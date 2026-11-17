package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import catalog.VersionRow
import catalog.versionRowsOf
import designsystem.Spacing
import model.MediaSet
import model.WatchSnapshot
import ui.chrome.CatalogueTextButton
import ui.common.catalog.VersionAsk

/**
 * A film held more than once, drawn as the choice it is — the web's
 * `film-versions-view.js`: one row per copy in version order, with its
 * label, its technical line, where this viewer is in it, and its own Play.
 * The film page lists them under its header; the chooser asks with them.
 */
@Composable
internal fun VersionRows(
    rows: List<VersionRow>,
    onPlay: (MediaSet) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        for (row in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(row.label, style = MaterialTheme.typography.titleSmall)
                    for (line in listOfNotNull(row.technical.ifEmpty { null }, row.state)) {
                        Text(line, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (row.held) OfflineBadge(modifier = Modifier.padding(top = Spacing.extraSmall))
                }
                PagePill(
                    text = "▶ ${row.action}",
                    onClick = { onPlay(row.set) },
                    modifier = Modifier.semantics { contentDescription = "${row.action} ${row.label}" },
                )
            }
        }
    }
}

/** A film page's Versions section, standing in for its Play: every copy has its own. */
@Composable
internal fun VersionsPanel(
    film: MediaSet,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    onPlay: (MediaSet) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Text("Versions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Spacing.small))
        VersionRows(versionRowsOf(film, watch, heldIds), onPlay)
    }
}

/**
 * Which copy to play, asked from a card that plays straight away. A bottom
 * sheet here — the platform's widget for a short pick-one list — where the
 * web asks in a centred dialog and the television in its own pick-one
 * surface: the one deliberate difference between the surfaces. Dismissing
 * it plays nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun VersionAskSheet(
    ask: VersionAsk,
    watch: WatchSnapshot,
    heldIds: Set<String>,
) {
    val question = ask.question ?: return
    ModalBottomSheet(onDismissRequest = ask::cancel) {
        Column(modifier = Modifier.padding(horizontal = Spacing.large).padding(bottom = Spacing.large)) {
            Text(question.film.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = Spacing.medium))
            VersionRows(versionRowsOf(question.film, watch, heldIds), onPlay = ask::choose)
            CatalogueTextButton(onClick = ask::cancel, modifier = Modifier.align(Alignment.End)) { Text("Cancel") }
        }
    }
}
