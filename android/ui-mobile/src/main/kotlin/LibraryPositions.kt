package ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue

/**
 * Where in the library a viewer currently is: whichever show or course the
 * catalog opened, whichever title that described, whichever set that
 * played, the system screen, and the TMDB key screen.
 *
 * All five are saved rather than remembered: the Activity is fully
 * destroyed and recreated on rotation (there is no `android:configChanges`),
 * and the singleton player survives that regardless — without this,
 * rotating away from an open set would drop back to the catalog while the
 * film kept playing underneath it.
 *
 * The collection and the opened title are held as keys and looked up again,
 * not kept as trees or sets: a saved position has to survive the process
 * being killed, and a key is a short string where a course is a few hundred
 * sets.
 */
internal class LibraryPositions(
    setId: MutableState<String?>,
    titleId: MutableState<String?>,
    collection: MutableState<String?>,
    system: MutableState<Boolean>,
    tmdbKey: MutableState<Boolean>,
) {
    var setId: String? by setId
    var titleId: String? by titleId
    var collection: String? by collection
    var system: Boolean by system
    var tmdbKey: Boolean by tmdbKey

    /**
     * Back to the shelves from wherever, all at once. Asked for by an
     * action whose result is the shelves themselves: a viewer who requests
     * the library from a screen that cannot show it has to be shown it.
     */
    fun toCatalog() {
        setId = null
        titleId = null
        collection = null
        system = false
        tmdbKey = false
    }
}

@Composable
internal fun rememberLibraryPositions(): LibraryPositions = LibraryPositions(
    setId = rememberSaveable { mutableStateOf<String?>(null) },
    titleId = rememberSaveable { mutableStateOf<String?>(null) },
    collection = rememberSaveable { mutableStateOf<String?>(null) },
    system = rememberSaveable { mutableStateOf(false) },
    tmdbKey = rememberSaveable { mutableStateOf(false) },
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
    onLeave: () -> Unit,
    content: @Composable () -> Unit,
) {
    BackHandler(onBack = onLeave)
    LibraryScaffold(destination = destination, onBack = onLeave, menu = menu, content = content)
}
