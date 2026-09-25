package ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
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
 * surface without being written twice; this only holds the six keys — as
 * one saved [LibraryPositions] value rather than six independent pieces of
 * state, so a key added later ([libraryPositionsSaver] and [copy] are the
 * only other places it then has to be named) means editing one declaration
 * rather than five — and hands a plain copy of them to the pure model and
 * back — [snapshot] out, [applyFrom] in.
 */
class LibraryPositionsHolder(
    private val state: MutableState<LibraryPositions>,
) {
    var setId: String?
        get() = state.value.setId
        set(value) {
            state.value = state.value.copy(setId = value)
        }

    var titleId: String?
        get() = state.value.titleId
        set(value) {
            state.value = state.value.copy(titleId = value)
        }

    var collection: String?
        get() = state.value.collection
        set(value) {
            state.value = state.value.copy(collection = value)
        }

    var season: String?
        get() = state.value.season
        set(value) {
            state.value = state.value.copy(season = value)
        }

    /** Which hand-built list is open, by its own id — the Collections tab's counterpart to [collection]. */
    var listId: String?
        get() = state.value.listId
        set(value) {
            state.value = state.value.copy(listId = value)
        }

    var menuScreen: MenuScreen?
        get() = state.value.menuScreen
        set(value) {
            state.value = state.value.copy(menuScreen = value)
        }

    /** A plain copy of the held keys, for [catalog.LibraryPositions.resolve] to read and [leave] to clear. */
    fun snapshot(): LibraryPositions = state.value.copy()

    /** Replaces the held state with a resolved snapshot's clears — see [leaveFrom]. */
    private fun applyFrom(positions: LibraryPositions) {
        state.value = positions
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

/**
 * A distinct copy of [this] with the named fields replaced. [LibraryPositions]
 * stays a plain mutable class rather than a data class — its own file mutates
 * these fields in place as part of [catalog.LibraryPositions.resolve]'s and
 * [leave]'s semantics — so this holder builds its own copy where a data
 * class's generated one would otherwise go: replacing the state with a
 * distinct instance, rather than mutating the held one, is what a
 * [MutableState] needs to see the write and notify its readers.
 */
private fun LibraryPositions.copy(
    setId: String? = this.setId,
    titleId: String? = this.titleId,
    collection: String? = this.collection,
    season: String? = this.season,
    listId: String? = this.listId,
    menuScreen: MenuScreen? = this.menuScreen,
): LibraryPositions = LibraryPositions(setId, titleId, collection, season, listId, menuScreen)

/**
 * How the six keys cross a process death together, as one value: each key
 * saved as itself, and [MenuScreen] — an enum, so not directly parcelable —
 * saved by its name and looked back up with [MenuScreen.valueOf].
 */
private val libraryPositionsSaver: Saver<LibraryPositions, List<Any?>> =
    Saver(
        save = { positions ->
            listOf(
                positions.setId,
                positions.titleId,
                positions.collection,
                positions.season,
                positions.listId,
                positions.menuScreen?.name,
            )
        },
        restore = { saved ->
            LibraryPositions(
                setId = saved[0] as String?,
                titleId = saved[1] as String?,
                collection = saved[2] as String?,
                season = saved[3] as String?,
                listId = saved[4] as String?,
                menuScreen = (saved[5] as String?)?.let(MenuScreen::valueOf),
            )
        },
    )

@Composable
fun rememberLibraryPositions(): LibraryPositionsHolder =
    LibraryPositionsHolder(
        rememberSaveable(stateSaver = libraryPositionsSaver) {
            mutableStateOf(LibraryPositions())
        },
    )
