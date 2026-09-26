package ui.tv.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.Entry
import catalog.SearchDestination
import catalog.SearchRow
import catalog.VisiblePerson
import catalog.extentOf
import catalog.initialsOf
import catalog.searchWhy
import coil3.compose.AsyncImage
import designsystem.Spacing
import designsystem.TvTypeScale
import java.io.File
import ui.catalog.isPlayable
import ui.catalog.locationOf
import ui.catalog.rememberPortrait
import ui.catalog.searchMetaLineOf
import ui.tv.TvFocus
import ui.tv.TvTextRow

/** How wide a person's own portrait sits on their search row. */
private val SearchPortraitWidth = 56.dp

/**
 * One hit, as the phone's `SearchResultRow` draws it: the title, where it
 * sits, the summary line that matched, why it matched, what the file is,
 * and this viewer's progress. A press plays it straight away, as a tap does
 * on the phone — a search hit is a set, not a way to somewhere else.
 *
 * A document is shown and not opened, and reads as disabled, the rule a
 * course's own rows keep; the remote can still rest on it. Why it matched
 * is set apart by italics rather than the phone's accent colour: on a
 * television that red means only where the remote is.
 *
 * A title this device holds carries the phone's "offline" badge, from the
 * same [SearchRow.held] the phone reads.
 */
@Composable
internal fun TvSearchRow(
    row: SearchRow,
    progress: Float?,
    watched: Boolean,
    onPlay: (setId: String) -> Unit,
    focus: FocusRequester?,
) {
    val set = row.set
    val playable = isPlayable(set)
    // Read from the focus state itself, as TvTextRow does: a row focused on
    // arrival never hears a focus interaction.
    var focused by remember { mutableStateOf(false) }
    val quiet = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (focus != null) it.focusRequester(focus) else it }
                .onFocusChanged { focused = it.isFocused }
                .let {
                    if (playable) {
                        it.clickable(indication = null, interactionSource = null) { onPlay(set.setId) }
                    } else {
                        it.focusable().semantics(mergeDescendants = true) { disabled() }
                    }
                },
    ) {
        val base = if (playable) TvTypeScale.body else TvTypeScale.body.copy(color = quiet)
        Text(text = "${if (watched) "✓ " else ""}${set.title}", style = TvFocus.textStyle(base, focused))
        if (!playable) TvQuietLine(DocumentReason)
        locationOf(set)?.let { TvQuietLine(it) }
        // The reason a summary hit is worth showing at all.
        row.excerpt?.let { TvQuietLine(it) }
        searchWhy(row.matched)?.let { Text(text = it, style = TvTypeScale.body, fontStyle = FontStyle.Italic, color = quiet) }
        searchMetaLineOf(set).takeIf(String::isNotEmpty)?.let { TvQuietLine(it) }
        TvItemMarks(progress, row.held)
    }
}

/** A matched show, rolled up from its episodes rather than listed once per one — [catalog.searchGroupsOf]'s own rule. */
@Composable
internal fun TvShowSearchRow(
    entry: Entry.Collection,
    onOpenCollection: (key: String) -> Unit,
    focus: FocusRequester?,
) {
    TvTextRow(
        text = "${entry.name} · ${extentOf(entry)}",
        onClick = { onOpenCollection(entry.key) },
        modifier = Modifier.fillMaxWidth().let { if (focus != null) it.focusRequester(focus) else it },
    )
}

/**
 * A person the query matched, already narrowed to titles *this profile* can
 * see ([catalog.visiblePeople]'s own rule — never shown otherwise). A round
 * portrait, fetched lazily and at most once per session, the same rule
 * every cast row on this surface follows.
 */
@Composable
internal fun TvPersonSearchRow(
    person: VisiblePerson,
    onOpenPerson: (personId: Long) -> Unit,
    shouldRequestPortrait: (Long) -> Boolean,
    fetchPortrait: suspend (Long) -> String?,
    focus: FocusRequester?,
) {
    val portrait = rememberPortrait(person.personId, person.portraitPath, shouldRequestPortrait, fetchPortrait)
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (focus != null) it.focusRequester(focus) else it }
                .onFocusChanged { focused = it.isFocused }
                // Merged, the same reason `TvPlate`'s own Card is: the name
                // and title count are this row's one announcement, and a
                // press anywhere on it is the same one press.
                .semantics(mergeDescendants = true) {}
                .clickable(indication = null, interactionSource = null) { onOpenPerson(person.personId) },
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvPortraitCircle(portrait?.let(::File), person.name, modifier = Modifier.width(SearchPortraitWidth))
        Column {
            Text(text = person.name, style = TvFocus.textStyle(TvTypeScale.body, focused))
            TvQuietLine("${person.titles} ${if (person.titles == 1) "title" else "titles"}")
        }
    }
}

/** A person's portrait, round rather than a poster's rectangle, matching the web's own people avatars; [initialsOf] stands in for a missing one. */
@Composable
private fun TvPortraitCircle(
    path: File?,
    name: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .aspectRatio(1f)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (path != null) {
            AsyncImage(model = path, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        } else {
            Text(text = initialsOf(name), style = TvTypeScale.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** A franchise or the viewer's own list, matched by name — [catalog.SearchDestination]'s own two sources. */
@Composable
internal fun TvDestinationSearchRow(
    destination: SearchDestination,
    onOpenDestination: (SearchDestination) -> Unit,
    focus: FocusRequester?,
) {
    TvTextRow(
        text = "${destination.name} · ${destination.itemCount} ${if (destination.itemCount == 1) "title" else "titles"}",
        onClick = { onOpenDestination(destination) },
        modifier = Modifier.fillMaxWidth().let { if (focus != null) it.focusRequester(focus) else it },
    )
}
