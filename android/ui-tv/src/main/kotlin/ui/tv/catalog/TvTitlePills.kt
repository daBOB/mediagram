package ui.tv.catalog

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import catalog.SeriesResumePick
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import model.MediaSet
import model.WatchSnapshot
import ui.tv.TvFocus

/**
 * One of a title spread's pills — `.pill` in `title-page.css`: fully round,
 * 48dp tall. [solid] is the one that starts the title (`.pill-solid`, ink
 * on paper reversed); the rest are line pills (`.pill-line`, a rule border
 * over a faint wash). Focus is this surface's one treatment — the accent
 * ring and the scale every other control carries.
 */
@Composable
internal fun TvSpreadPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    solid: Boolean = false,
    description: String? = null,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    val container = if (solid) ink else ink.copy(alpha = 0.05f)
    val content = if (solid) MaterialTheme.colorScheme.background else ink
    Surface(
        onClick = onClick,
        modifier =
            modifier
                .heightIn(min = PillHeight)
                .semantics(mergeDescendants = true) { description?.let { contentDescription = it } },
        shape = TvFocus.surfaceShape(TvFocus.PillShape),
        colors =
            ClickableSurfaceDefaults.colors(
                containerColor = container,
                contentColor = content,
                focusedContainerColor = container,
                focusedContentColor = content,
                pressedContainerColor = container,
                pressedContentColor = content,
            ),
        scale = TvFocus.surfaceScale(),
        border =
            ClickableSurfaceDefaults.border(
                border = if (solid) Border.None else Border(BorderStroke(1.dp, MaterialTheme.colorScheme.border), shape = TvFocus.PillShape),
                focusedBorder = Border(BorderStroke(TvFocus.BorderWidth, Palette.Imprint), shape = TvFocus.PillShape),
            ),
        glow = TvFocus.surfaceGlow(),
    ) {
        Box(modifier = Modifier.heightIn(min = PillHeight).padding(horizontal = Spacing.large), contentAlignment = Alignment.Center) {
            Text(text = text, style = TvTypeScale.body.copy(color = content))
        }
    }
}

/**
 * "My List" — `list-toggle.js`'s pill: its mark (`+`, or `✓` while the
 * title is on the list) before the list's name, which stays "My List" either
 * way, as on the web's and the phone's title pages. The players' button says
 * "On My List" instead ([player.listLabel]): it has no mark to carry the state.
 */
@Composable
internal fun TvListPill(
    watchlisted: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TvSpreadPill(
        text = "${if (watchlisted) "✓" else "+"} My List",
        onClick = onToggle,
        modifier = modifier,
        description = if (watchlisted) "Remove from My List" else "Add to My List",
    )
}

/**
 * The ⋯ beside the pills (`moreMenu` in `title-spread.js`): what a page has
 * room for but should not lead with — today the editor's-choice toggle. Its
 * choices open in the pill row itself, after the button, rather than in a
 * list floating under it, where they would sit over the tab row the remote
 * reaches with Down.
 *
 * Pressing ⋯ moves the remote onto the first choice; pressing a choice, or
 * Back, puts it back on ⋯ before the choices go, so the remote is never
 * left on a stop that no longer exists. Leaving the row any other way
 * closes it too. Nothing is drawn for no [choices], as the web leaves the
 * menu out on a kids profile.
 */
@Composable
internal fun TvMorePill(
    choices: List<Pair<String, () -> Unit>>,
    modifier: Modifier = Modifier,
) {
    if (choices.isEmpty()) return
    var open by remember { mutableStateOf(false) }
    val button = remember { FocusRequester() }
    val first = remember { FocusRequester() }
    val close = {
        button.requestFocus()
        open = false
    }
    Row(
        modifier = modifier.onFocusChanged { if (!it.hasFocus) open = false }.focusGroup(),
        horizontalArrangement = Arrangement.spacedBy(PillGap),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TvSpreadPill(text = "⋯", onClick = { open = !open }, modifier = Modifier.focusRequester(button), description = "More")
        if (open) {
            choices.forEachIndexed { index, (label, choose) ->
                // Never omitted — see the same doc on `TvResumeCard`'s own `ownRequester`.
                val own = remember { FocusRequester() }
                TvSpreadPill(
                    text = label,
                    onClick = {
                        close()
                        choose()
                    },
                    modifier = Modifier.focusRequester(if (index == 0) first else own),
                )
            }
        }
    }
    LaunchedEffect(open) { if (open) first.requestFocus() }
    BackHandler(enabled = open, onBack = close)
}

/**
 * A show's pills — `series-page.js`'s own: the resume pick ([resumeLabel])
 * when there is one, My List and ⋯ for the show's first episode, which is
 * how the web keeps a show on the list and pins it. [play] and [listPill]
 * are always attached to their own pill, so a page can land the remote on
 * whichever leads.
 */
@Composable
internal fun TvSeriesPills(
    first: MediaSet?,
    resume: SeriesResumePick?,
    onResume: (setId: String) -> Unit,
    watch: WatchSnapshot,
    onToggleWatchlist: () -> Unit,
    editorsChoice: String?,
    onToggleEditorsChoice: (() -> Unit)?,
    play: FocusRequester,
    listPill: FocusRequester,
) {
    TvPillRow {
        resume?.let { pick ->
            TvSpreadPill(text = resumeLabel(pick), onClick = { onResume(pick.set.setId) }, solid = true, modifier = Modifier.focusRequester(play))
        }
        if (first != null) {
            TvListPill(watchlisted = first.setId in watch.watchlist, onToggle = onToggleWatchlist, modifier = Modifier.focusRequester(listPill))
            TvMorePill(editorsChoiceChoice(onToggleEditorsChoice, pinned = editorsChoice == first.setId))
        }
    }
}

/** `.spread-actions`: the pills in a wrapping row, 12dp apart, a text row among them centred on their height. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TvPillRow(content: @Composable () -> Unit) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(PillGap),
        verticalArrangement = Arrangement.spacedBy(PillGap),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** `.spread-actions{gap:12px}`. */
private val PillGap = 12.dp

/** `.pill{min-height:48px}`. */
private val PillHeight = 48.dp
