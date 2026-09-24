package catalog

import model.ListOfSets
import model.MediaSet
import model.WatchSnapshot

/**
 * A screen the overflow menu opens, over whatever the library is showing,
 * and the name the bar gives it.
 *
 * One value rather than a flag each, because only one of them is ever on
 * screen and the menu that opens them is reachable from both of them. As
 * two independent flags, asking for the key screen from the system screen
 * set a flag the branch below never reached — nothing happened, and back
 * then cleared the system screen and landed on a key screen the viewer had
 * long since stopped asking for. A slot that holds one thing cannot do
 * that: asking for a screen is a move, not an addition.
 */
enum class MenuScreen(
    val destination: Destination,
) {
    System(Destination.System),
    TmdbKey(Destination.TmdbKey),
    Settings(Destination.Settings),
}

/**
 * Where in the library a viewer currently is: whichever show or course the
 * catalog opened, whichever season of it that opened from its wall,
 * whichever title that described, whichever set that played, whichever
 * hand-built list the Collections tab opened, and whichever screen the menu
 * opened over them.
 *
 * The six keys are plain properties rather than Compose state: this class
 * only holds and resolves them. A caller that wants them to survive
 * rotation and process death wraps this in its own `rememberSaveable`
 * holder — see `ui.rememberLibraryPositions` — so this class stays usable
 * from a plain unit test and from a second surface that renders the keys
 * differently.
 *
 * The collection, the season within it, the opened title, and the open list
 * are held as keys and looked up again, not kept as trees or sets: a saved
 * position has to survive the process being killed, and a key is a short
 * string where a course is a few hundred sets. A season is keyed by its
 * division's title rather than its number, so "Episodes" and specials —
 * which carry no number — resolve the same way a numbered season does.
 */
class LibraryPositions(
    var setId: String? = null,
    var titleId: String? = null,
    var collection: String? = null,
    var season: String? = null,
    /** Which hand-built list is open, by its own id — the Collections tab's counterpart to [collection]. */
    var listId: String? = null,
    var menuScreen: MenuScreen? = null,
) {
    /**
     * Back to the shelves from wherever, all at once. Asked for by an
     * action whose result is the shelves themselves: a viewer who requests
     * the library from a screen that cannot show it has to be shown it.
     */
    fun toCatalog() {
        setId = null
        titleId = null
        collection = null
        season = null
        listId = null
        menuScreen = null
    }

    /**
     * What the library shows for these keys against [state] — the branch
     * priority the library composition used to re-decide at every
     * recomposition: the player first, since it is the one destination that
     * fills the whole window; then whatever the menu opened, since it sits
     * over any of the rest; then a title, a season within its collection
     * (checked ahead of the collection itself, since a season is a screen
     * the collection's wall opened over it), the collection, a hand-built
     * list, and last the shelves underneath all of them.
     *
     * Keys that name something the library no longer holds — a stale id
     * from before a reload, or one still resolving while the library loads
     * — are skipped as though unset, which is what lets a restored position
     * come back once the library it names finishes loading.
     */
    fun resolve(state: CatalogUiState): ResolvedPosition {
        val openSetId = setId
        val openMenu = menuScreen
        val title = titleId?.let(state::mediaSet)
        val collectionEntry = collection?.let(state::collection)
        // Resolved from the collection rather than saved as a tree: a
        // season is one of its show's own divisions, so it only exists
        // once the show it belongs to does, and a stale key from a
        // different show simply fails to find a match here rather than
        // opening the wrong season.
        val seasonDivision = season?.let { name -> collectionEntry?.divisions?.find { it.title == name } }
        val list = listId?.let { id -> state.watchSnapshot().collections.find { it.id == id } }
        return when {
            openSetId != null -> ResolvedPosition.Player(openSetId)
            openMenu != null -> ResolvedPosition.Menu(openMenu)
            title != null -> ResolvedPosition.TitleOpen(title)
            seasonDivision != null -> ResolvedPosition.SeasonOpen(seasonDivision)
            collectionEntry != null -> ResolvedPosition.CollectionOpen(collectionEntry)
            list != null -> ResolvedPosition.ListOpen(list)
            else -> ResolvedPosition.Catalog
        }
    }
}

/** The snapshot a position's list lookup reads against, or none while the library is not yet ready. */
private fun CatalogUiState.watchSnapshot(): WatchSnapshot = (this as? CatalogUiState.Ready)?.watch ?: WatchSnapshot.Empty

/**
 * What [LibraryPositions.resolve] found for a set of keys — the destination
 * shown, carrying whatever it resolved rather than only the key that named
 * it, so the screen rendering it does not look the same thing up twice.
 */
sealed interface ResolvedPosition {
    data class Player(
        val setId: String,
    ) : ResolvedPosition

    data class Menu(
        val screen: MenuScreen,
    ) : ResolvedPosition

    data class TitleOpen(
        val title: MediaSet,
    ) : ResolvedPosition

    data class SeasonOpen(
        val season: Division,
    ) : ResolvedPosition

    data class CollectionOpen(
        val collection: Entry.Collection,
    ) : ResolvedPosition

    data class ListOpen(
        val list: ListOfSets,
    ) : ResolvedPosition

    data object Catalog : ResolvedPosition
}

/**
 * What leaving [this] clears in [positions] — the back order [resolve]
 * checks in reverse: the player and the menu each clear only their own key,
 * a title clears only itself, a season clears only itself and uncovers its
 * collection, a hand-built list clears only itself, and the catalog is the
 * top of the tree and clears nothing because there is nowhere further back
 * to go.
 *
 * The collection is the one branch that clears two keys: it also drops
 * [LibraryPositions.season], because a season left behind here would
 * otherwise resolve against whichever collection is opened next, and a
 * different show can easily have a division of the same name — "Season 1"
 * is not a fact about one show.
 */
fun ResolvedPosition.leave(positions: LibraryPositions) {
    when (this) {
        is ResolvedPosition.Player -> positions.setId = null
        is ResolvedPosition.Menu -> positions.menuScreen = null
        is ResolvedPosition.TitleOpen -> positions.titleId = null
        is ResolvedPosition.SeasonOpen -> positions.season = null
        is ResolvedPosition.CollectionOpen -> {
            positions.collection = null
            positions.season = null
        }
        is ResolvedPosition.ListOpen -> positions.listId = null
        ResolvedPosition.Catalog -> {}
    }
}
