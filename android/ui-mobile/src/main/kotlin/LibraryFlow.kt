package ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogViewModel
import catalog.collection

/**
 * The catalog, whichever show or course it opened, whichever set that
 * played, and the system screen — the first screens here with a real
 * back-stack need.
 *
 * All three positions are saved rather than remembered: the Activity is
 * fully destroyed and recreated on rotation (there is no
 * `android:configChanges`), and the singleton player survives that
 * regardless — without this, rotating away from an open set would drop
 * back to the catalog while the film kept playing underneath it.
 *
 * The collection is held as its key and looked up again, not kept as a tree:
 * a saved position has to survive the process being killed, and a key is a
 * short string where a course is a few hundred sets.
 */
@Composable
internal fun CatalogAndPlayer(onStartOver: () -> Unit) {
    val catalogViewModel: CatalogViewModel = hiltViewModel()
    val catalogState by catalogViewModel.state.collectAsStateWithLifecycle()
    var openedSetId by rememberSaveable { mutableStateOf<String?>(null) }
    var openedCollection by rememberSaveable { mutableStateOf<String?>(null) }
    var showingSystem by rememberSaveable { mutableStateOf(false) }

    val setId = openedSetId
    // Derived from the collected state, so the collection appears of its
    // own accord when the library finishes loading — which is what brings a
    // restored position back to the course it was in.
    val collection = openedCollection?.let(catalogState::collection)

    // Fetch posters and the TMDB key are wired to real behaviour by later
    // phases; the menu carries their slot from the start so those phases
    // add behaviour rather than UI.
    val menuActions = MenuActions(
        onSystem = { showingSystem = true },
        onFetchPosters = {},
        onTmdbKey = {},
        onStartOver = onStartOver,
    )

    when {
        setId != null -> {
            // The player gets the whole window; a film is the one thing here
            // that wants the space under the system bars.
            BackHandler { openedSetId = null }
            PlayerScreen(setId = setId, onBack = { openedSetId = null })
        }

        showingSystem -> {
            // backLabelFor(Destination.System) is "Back": a sub screen, same
            // as a collection, always offers a way back.
            BackHandler { showingSystem = false }
            LibraryScaffold(
                title = barTitleFor(Destination.System),
                onBack = { showingSystem = false },
                menu = menuActions,
            ) { SystemPlaceholder() }
        }

        collection != null -> {
            BackHandler { openedCollection = null }
            LibraryScaffold(
                title = barTitleFor(Destination.Collection(collection.name)),
                onBack = { openedCollection = null },
                menu = menuActions,
            ) {
                CollectionScreen(collection = collection, onPlay = { openedSetId = it })
            }
        }

        // Also where a saved key lands while the library is still loading,
        // and where one that no longer names anything stays: the shelves are
        // the right thing to show in both cases, and clearing the key here
        // would throw away a position that is about to resolve.
        else -> {
            // backLabelFor(Destination.Catalog) is null: the catalog is the
            // top of the tree and offers no way back.
            LibraryScaffold(
                title = barTitleFor(Destination.Catalog),
                onBack = null,
                menu = menuActions,
            ) {
                CatalogScreen(
                    state = catalogState,
                    onPlay = { openedSetId = it },
                    onOpenCollection = { openedCollection = it },
                )
            }
        }
    }
}

// Stands in for the real system screen until it exists; the destination,
// the bar and the menu around it are already real.
@Composable
private fun SystemPlaceholder() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text("System")
    }
}
