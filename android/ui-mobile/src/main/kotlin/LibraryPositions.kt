package ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

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
internal enum class MenuScreen(val destination: Destination) {
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
 * All six are saved rather than remembered: the Activity is fully
 * destroyed and recreated on rotation (there is no `android:configChanges`),
 * and the singleton player survives that regardless — without this,
 * rotating away from an open set would drop back to the catalog while the
 * film kept playing underneath it.
 *
 * The collection, the season within it, the opened title, and the open list
 * are held as keys and looked up again, not kept as trees or sets: a saved
 * position has to survive the process being killed, and a key is a short
 * string where a course is a few hundred sets. A season is keyed by its
 * division's title rather than its number, so "Episodes" and specials —
 * which carry no number — resolve the same way a numbered season does.
 */
internal class LibraryPositions(
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
}

@Composable
internal fun rememberLibraryPositions(): LibraryPositions = LibraryPositions(
    setId = rememberSaveable { mutableStateOf<String?>(null) },
    titleId = rememberSaveable { mutableStateOf<String?>(null) },
    collection = rememberSaveable { mutableStateOf<String?>(null) },
    season = rememberSaveable { mutableStateOf<String?>(null) },
    listId = rememberSaveable { mutableStateOf<String?>(null) },
    menuScreen = rememberSaveable { mutableStateOf<MenuScreen?>(null) },
)

/**
 * One screen of the library under the app's chrome, and what leaving it
 * means.
 *
 * The system back gesture and the bar's back arrow are the same departure
 * said twice, so they are given the same lambda here rather than at each
 * branch — a screen that wired one and forgot the other would go back in
 * two different places depending on which the viewer reached for.
 */
@Composable
internal fun LibraryBranch(
    destination: Destination,
    menu: MenuActions,
    profile: ProfileBarState,
    onLeave: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onLeave)
    LibraryScaffold(destination = destination, onBack = onLeave, menu = menu, profile = profile, content = content)
}
