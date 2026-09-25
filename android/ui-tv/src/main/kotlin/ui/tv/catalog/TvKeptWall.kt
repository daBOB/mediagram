package ui.tv.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.text.style.TextAlign
import catalog.KeptKind
import catalog.SetCard
import catalog.resumeLine
import catalog.watchedFractionOf
import designsystem.Overscan
import model.MediaSet
import model.Progress
import model.WatchSnapshot

/**
 * One of the two kept walls that hold titles directly — Continue and
 * Watchlist — the television twin of the phone's `KeptWall`. Each
 * plate is a [TvSetPlate], the plate Continue and Next up already use on
 * Home, captioned with [resumeLine] as the phone and `setGrid` caption
 * theirs: most Watchlist plates have no position to report and say nothing
 * under the name.
 *
 * Collections is not one of these: its shelf is a list of lists, not of
 * titles, and lives in [TvLists].
 */
@Composable
internal fun TvKeptWall(
    kind: KeptKind,
    sets: List<MediaSet>,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    tabFocus: FocusRequester,
    restoreKey: String? = null,
) {
    if (sets.isEmpty()) {
        EmptyKeptWall(kind, tabFocus)
        return
    }
    val (positions, watchedIds) = rememberWatchMarks(watch)
    TvWall(
        items = sets,
        key = MediaSet::setId,
        restoreKey = restoreKey,
        onOpen = { set -> onOpenTitle(set.setId) },
        header = { TvCountedHeading(kind.label, sets.size) },
        plate = { set, modifier, onOpen -> KeptSetPlate(set, positions, watchedIds, onOpen, modifier) },
    )
}

@Composable
private fun KeptSetPlate(
    set: MediaSet,
    positions: Map<String, Progress>,
    watchedIds: Set<String>,
    onOpen: () -> Unit,
    modifier: Modifier,
) {
    TvSetPlate(
        card =
            SetCard(
                set = set,
                caption = resumeLine(positions[set.setId]),
                progress = watchedFractionOf(positions[set.setId]),
                watched = set.setId in watchedIds,
            ),
        onOpen = onOpen,
        modifier = modifier,
    )
}

/**
 * The heading still stands over an empty wall, as on the phone — it says
 * which tab this is — with the phone's own empty text under it. Nothing
 * here takes focus, so the remote goes up to this wall's own tab on the
 * masthead: already there when the tab was just chosen, but not when the
 * wall empties under the viewer — the last title taken off the Watchlist
 * from its own page — when it would otherwise be left resting on nothing,
 * or on whatever tab the window's own search picked for it.
 */
@Composable
private fun EmptyKeptWall(
    kind: KeptKind,
    tabFocus: FocusRequester,
) {
    LaunchedEffect(Unit) { tabFocus.requestFocus() }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical)) {
        TvCountedHeading(kind.label, 0)
        // Centred in what is left, not through TvCenteredMessage: this
        // column already stands inside the overscan inset.
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            TvQuietLine(kind.empty, textAlign = TextAlign.Center)
        }
    }
}
