package ui

import androidx.activity.compose.BackHandler
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogViewModel
import catalog.collection

/**
 * The catalog, whichever show or course it opened, and whichever set that
 * played — the first screens here with a real back-stack need.
 *
 * Both positions are saved rather than remembered: the Activity is fully
 * destroyed and recreated on rotation (there is no `android:configChanges`),
 * and the singleton player survives that regardless — without this, rotating
 * away from an open set would drop back to the catalog while the film kept
 * playing underneath it.
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

    val setId = openedSetId
    // Derived from the collected state, so the collection appears of its
    // own accord when the library finishes loading — which is what brings a
    // restored position back to the course it was in.
    val collection = openedCollection?.let(catalogState::collection)
    when {
        setId != null -> {
            // The player gets the whole window; a film is the one thing here
            // that wants the space under the system bars.
            BackHandler { openedSetId = null }
            PlayerScreen(setId = setId, onBack = { openedSetId = null })
        }

        collection != null -> {
            BackHandler { openedCollection = null }
            WithStartOver(onStartOver) {
                CollectionScreen(collection = collection, onPlay = { openedSetId = it })
            }
        }

        // Also where a saved key lands while the library is still loading,
        // and where one that no longer names anything stays: the shelves are
        // the right thing to show in both cases, and clearing the key here
        // would throw away a position that is about to resolve.
        else -> {
            WithStartOver(onStartOver) {
                CatalogScreen(
                    state = catalogState,
                    onPlay = { openedSetId = it },
                    onOpenCollection = { openedCollection = it },
                )
            }
        }
    }
}
