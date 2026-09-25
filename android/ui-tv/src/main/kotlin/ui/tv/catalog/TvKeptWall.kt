package ui.tv.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
 * under the name. A title this device holds carries the offline badge.
 *
 * [onFinish] puts "Mark finished" beneath each plate — Continue's, for a
 * film finished on another device or given up on, which would otherwise
 * sit here until played to the credits. Beneath the plate, one press down,
 * for the reason the web's `withAction` keeps it beside the card and the
 * phone under it: Centre on a plate still opens it, and nothing a viewer
 * does on the way to starting a title finishes it by mistake. Chosen over a
 * long press of Centre, which nothing on screen announces, and over a row
 * on the title page, which the phone and the web do not have: this is
 * where both of them put it, and where a list's "Remove" already sits on
 * this surface. The finished title leaves the wall, so the remote moves to
 * the one beside it, or up to the tab when it was the last.
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
    heldIds: Set<String> = emptySet(),
    onFinish: ((setId: String) -> Unit)? = null,
) {
    if (sets.isEmpty()) {
        EmptyKeptWall(kind, tabFocus)
        return
    }
    val (positions, watchedIds) = rememberWatchMarks(watch)
    var afterFinish by remember { mutableStateOf<String?>(null) }
    TvWall(
        items = sets,
        key = MediaSet::setId,
        restoreKey = afterFinish ?: restoreKey,
        onOpen = { set -> onOpenTitle(set.setId) },
        header = { TvCountedHeading(kind.label, sets.size) },
        plate = { set, modifier, onOpen ->
            val plate = @Composable { KeptSetPlate(set, positions, watchedIds, set.setId in heldIds, onOpen, modifier) }
            if (onFinish == null) {
                plate()
            } else {
                TvPlateWithAction(label = MarkFinished, onAction = {
                    afterFinish = neighbourOf(sets, MediaSet::setId, set.setId)
                    onFinish(set.setId)
                }, plate = plate)
            }
        },
    )
}

/** The phone's and the web's own words for it. */
internal const val MarkFinished = "Mark finished"

@Composable
private fun KeptSetPlate(
    set: MediaSet,
    positions: Map<String, Progress>,
    watchedIds: Set<String>,
    held: Boolean,
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
                held = held,
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
    val takesFocus = LocalTakesArrivalFocus.current
    LaunchedEffect(Unit) { if (takesFocus) tabFocus.requestFocus() }
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical)) {
        TvCountedHeading(kind.label, 0)
        // Centred in what is left, not through TvCenteredMessage: this
        // column already stands inside the overscan inset.
        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
            TvQuietLine(kind.empty, textAlign = TextAlign.Center)
        }
    }
}
