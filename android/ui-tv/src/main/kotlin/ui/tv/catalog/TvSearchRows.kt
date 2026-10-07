package ui.tv.catalog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.SearchRow
import catalog.initialsOf
import catalog.searchWhy
import coil3.compose.AsyncImage
import data.PortraitRequestLog
import designsystem.Spacing
import designsystem.TvTypeScale
import java.io.File
import ui.common.catalog.isPlayable
import ui.common.catalog.locationOf
import ui.common.catalog.rememberPortrait
import ui.common.catalog.searchMetaLineOf
import ui.tv.TvFocus
import ui.tv.rememberStableRequester

/**
 * One episode, documentary or lesson hit, as the phone's `SearchResultRow` draws it: the title, where it
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
                .focusRequester(rememberStableRequester(focus))
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

/**
 * A person, as the web's `personCard` draws one: a round portrait, the name
 * under it, and one quiet line under that — [sub], how many titles a search
 * match is in, or the character a cast member plays. Search's people are
 * already narrowed to titles *this profile* can see
 * ([catalog.visiblePeople]'s own rule — never shown otherwise). The
 * portrait is fetched lazily and at most once per session, the rule every
 * cast row on this surface follows.
 *
 * Focus wears the one treatment every card here does — the accent ring and
 * [TvFocus.Scale] — drawn on the round face by hand, since a tv-material
 * `Card` clips its content to its shape and the name has to sit under the
 * circle rather than inside it. Both modifiers are always present and only
 * their values follow focus, so the chain never changes shape under the
 * remote. [modifier] carries the caller's width and requester.
 */
@Composable
internal fun TvPersonCard(
    personId: Long,
    name: String,
    portraitPath: String?,
    sub: String?,
    onOpenPerson: (personId: Long) -> Unit,
    portraits: PortraitRequestLog,
    fetchPortrait: suspend (Long) -> String?,
    modifier: Modifier = Modifier,
) {
    val portrait = rememberPortrait(personId, portraitPath, portraits, fetchPortrait)
    var focused by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(if (focused) TvFocus.Scale else 1f, label = "person-card-scale")
    Column(
        modifier =
            modifier
                .onFocusChanged { focused = it.isFocused }
                // Merged, the same reason `TvPlate`'s own Card is: the name
                // and the line under it are this card's one announcement,
                // and a press anywhere on it is the same one press.
                .semantics(mergeDescendants = true) {}
                .clickable(indication = null, interactionSource = null) { onOpenPerson(personId) },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        TvPortraitCircle(
            portrait?.let(::File),
            name,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }.border(TvFocus.BorderWidth, if (focused) MaterialTheme.colorScheme.primary else Color.Transparent, CircleShape),
        )
        Text(
            text = name,
            style = TvTypeScale.body,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.small),
        )
        sub?.let { TvQuietLine(it, textAlign = TextAlign.Center) }
    }
}

/** A person's portrait, round rather than a poster's rectangle, matching the web's own people avatars; [initialsOf] stands in for a missing one. */
@Composable
internal fun TvPortraitCircle(
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
