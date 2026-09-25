package ui.tv.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import catalog.Entry
import catalog.KeptKind
import catalog.KidsShelf
import catalog.SetCard
import catalog.keyOf
import catalog.resumeLine
import catalog.watchedFractionOf
import designsystem.Overscan
import model.MediaSet
import model.Progress
import model.WatchSnapshot

/**
 * One of the three kept walls that hold titles directly — Continue,
 * Watchlist, Kids — the television twin of the phone's `KeptWall`. Each
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
    restoreKey: String? = null,
) {
    if (sets.isEmpty()) {
        EmptyKeptWall(kind)
        return
    }
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    TvWall(
        items = sets,
        key = MediaSet::setId,
        restoreKey = restoreKey,
        onOpen = { set -> onOpenTitle(set.setId) },
        header = { TvCountedHeading(kind.label, sets.size) },
        plate = { set, modifier, onOpen -> KeptSetPlate(set, positions, watchedIds, onOpen, modifier) },
    )
}

/**
 * The Kids tab — `viewKids` in app.js, and the phone's `KidsWall`: what the
 * ratings put there, then what was marked by hand, each run under its own
 * heading once there is more than one kind to tell apart. A show's plate
 * opens the show, as it does on its own shelf; a hand-marked title is a
 * set plate, as on the other kept walls.
 */
@Composable
internal fun TvKidsWall(
    shelf: KidsShelf,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    restoreKey: String? = null,
) {
    if (shelf.total == 0) {
        EmptyKeptWall(KeptKind.KIDS)
        return
    }
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    val items =
        remember(shelf) {
            shelf.films.map { KidsItem.Rated(it, "Movies") } +
                shelf.series.map { KidsItem.Rated(it, "Series") } +
                shelf.byHand.map { KidsItem.ByHand(it) }
        }
    val headed = listOf(shelf.films, shelf.series, shelf.byHand).count { it.isNotEmpty() } > 1
    TvWall(
        items = items,
        key = KidsItem::key,
        restoreKey = restoreKey,
        onOpen = { item ->
            when (item) {
                is KidsItem.Rated -> openEntry(item.entry, onOpenTitle, onOpenCollection)
                is KidsItem.ByHand -> onOpenTitle(item.set.setId)
            }
        },
        header = { TvCountedHeading(KeptKind.KIDS.label, shelf.total) },
        section = if (headed) KidsItem::section else null,
        plate = { item, modifier, onOpen ->
            when (item) {
                is KidsItem.Rated -> TvEntryPlate(item.entry, positions, watchedIds, onOpen, modifier)
                is KidsItem.ByHand -> KeptSetPlate(item.set, positions, watchedIds, onOpen, modifier)
            }
        },
    )
}

/**
 * One plate of the Kids wall. A hand-marked title is keyed apart from a
 * rated one, as the phone keys it — the two runs are drawn from different
 * sources and nothing but that prefix keeps one title from colliding.
 */
private sealed interface KidsItem {
    val key: String
    val section: String

    data class Rated(val entry: Entry, override val section: String) : KidsItem {
        override val key: String get() = keyOf(entry)
    }

    data class ByHand(val set: MediaSet) : KidsItem {
        override val key: String get() = "hand-${set.setId}"
        override val section: String get() = "Marked by hand"
    }
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
 * here takes focus: the remote stays on the masthead tab that led here.
 */
@Composable
private fun EmptyKeptWall(kind: KeptKind) {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical)) {
        TvCountedHeading(kind.label, 0)
        // Centred in what is left, not through TvCenteredMessage: this
        // column already stands inside the overscan inset.
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            TvQuietLine(kind.empty, textAlign = TextAlign.Center)
        }
    }
}
