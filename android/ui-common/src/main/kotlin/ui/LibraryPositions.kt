package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import catalog.LibraryPositions
import catalog.MenuScreen
import catalog.ResolvedPosition
import catalog.leave

/**
 * The six keys [catalog.LibraryPositions] resolves, kept across a rotation
 * and a process death — the Activity is fully destroyed and recreated on
 * rotation (there is no `android:configChanges`), and the singleton player
 * survives that regardless — without this, rotating away from an open set
 * would drop back to the catalog while the film kept playing underneath it.
 *
 * The resolving, the branch priority and what back clears are
 * [catalog.LibraryPositions]'s own, so they work the same on a second
 * surface without being written twice; this only holds the six keys as
 * Compose state and hands a plain copy of them to the pure model and back —
 * [snapshot] out, [applyFrom] in.
 */
class LibraryPositionsHolder(
    setId: MutableState<String?>,
    titleId: MutableState<String?>,
    collection: MutableState<String?>,
    season: MutableState<String?>,
    listId: MutableState<String?>,
    menuScreen: MutableState<MenuScreen?>,
) {
    var setId: String? by setId
    var titleId: String? by titleId
    var collection: String? by collection
    var season: String? by season

    /** Which hand-built list is open, by its own id — the Collections tab's counterpart to [collection]. */
    var listId: String? by listId
    var menuScreen: MenuScreen? by menuScreen

    /** A plain copy of these six keys, for [catalog.LibraryPositions.resolve] to read and [leave] to clear. */
    fun snapshot(): LibraryPositions = LibraryPositions(setId, titleId, collection, season, listId, menuScreen)

    /** Writes a resolved snapshot's clears back to these Compose states — see [leaveFrom]. */
    private fun applyFrom(positions: LibraryPositions) {
        setId = positions.setId
        titleId = positions.titleId
        collection = positions.collection
        season = positions.season
        listId = positions.listId
        menuScreen = positions.menuScreen
    }

    /** What leaving [resolved] clears here — [ResolvedPosition.leave] decides what, this only carries it out. */
    fun leaveFrom(resolved: ResolvedPosition) {
        val positions = snapshot()
        resolved.leave(positions)
        applyFrom(positions)
    }

    /**
     * Back to the shelves from wherever, all at once. Asked for by an
     * action whose result is the shelves themselves: a viewer who requests
     * the library from a screen that cannot show it has to be shown it.
     */
    fun toCatalog() {
        val positions = snapshot()
        positions.toCatalog()
        applyFrom(positions)
    }
}

@Composable
fun rememberLibraryPositions(): LibraryPositionsHolder =
    LibraryPositionsHolder(
        setId = rememberSaveable { mutableStateOf<String?>(null) },
        titleId = rememberSaveable { mutableStateOf<String?>(null) },
        collection = rememberSaveable { mutableStateOf<String?>(null) },
        season = rememberSaveable { mutableStateOf<String?>(null) },
        listId = rememberSaveable { mutableStateOf<String?>(null) },
        menuScreen = rememberSaveable { mutableStateOf<MenuScreen?>(null) },
    )
