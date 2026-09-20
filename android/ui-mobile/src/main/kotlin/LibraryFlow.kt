package ui

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogViewModel
import catalog.collection
import system.PostersViewModel
import uniffi.mediagram_core.PosterReport

/**
 * The catalog, whichever show or course it opened, whichever set that
 * played, the system screen, and the TMDB key screen — the first screens
 * here with a real back-stack need.
 *
 * All four positions are saved rather than remembered: the Activity is
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
    val postersViewModel: PostersViewModel = hiltViewModel()
    val postersState by postersViewModel.state.collectAsStateWithLifecycle()
    var openedSetId by rememberSaveable { mutableStateOf<String?>(null) }
    var openedCollection by rememberSaveable { mutableStateOf<String?>(null) }
    var showingSystem by rememberSaveable { mutableStateOf(false) }
    var showingTmdbKey by rememberSaveable { mutableStateOf(false) }

    val setId = openedSetId
    // Derived from the collected state, so the collection appears of its
    // own accord when the library finishes loading — which is what brings a
    // restored position back to the course it was in.
    val collection = openedCollection?.let(catalogState::collection)

    val menuActions = MenuActions(
        onSystem = { showingSystem = true },
        onFetchPosters = postersViewModel::fetch,
        onTmdbKey = { showingTmdbKey = true },
        onStartOver = onStartOver,
        fetchPostersDisabledReason = fetchPostersDisabledReason(postersState.running, postersState.hasKey),
    )

    when {
        setId != null -> {
            // The player gets the whole window; a film is the one thing here
            // that wants the space under the system bars.
            BackHandler { openedSetId = null }
            PlayerScreen(setId = setId, onBack = { openedSetId = null })
        }

        showingSystem -> {
            BackHandler { showingSystem = false }
            LibraryScaffold(
                destination = Destination.System,
                onBack = { showingSystem = false },
                menu = menuActions,
            ) { SystemScreen() }
        }

        showingTmdbKey -> {
            BackHandler { showingTmdbKey = false }
            LibraryScaffold(
                destination = Destination.TmdbKey,
                onBack = { showingTmdbKey = false },
                menu = menuActions,
            ) {
                TmdbKeyScreen(hasKey = postersState.hasKey, onSave = postersViewModel::saveKey)
            }
        }

        collection != null -> {
            BackHandler { openedCollection = null }
            LibraryScaffold(
                destination = Destination.Collection(collection.name),
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
            // Never invoked: LibraryScaffold only wires this up when
            // backLabelFor(Destination.Catalog) says there is a way back,
            // and there is not — the catalog is the top of the tree.
            LibraryScaffold(
                destination = Destination.Catalog,
                onBack = {},
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

    PosterFetchResultDialog(
        message = posterFetchResultMessage(postersState.report, postersState.error),
        onDismiss = postersViewModel::dismissResult,
    )
}

/** Why "Fetch posters…" cannot be tapped right now, or `null` when it can. */
private fun fetchPostersDisabledReason(running: Boolean, hasKey: Boolean): String? = when {
    running -> "Fetching…"
    !hasKey -> "No TMDB key stored"
    else -> null
}

/** The sentence a finished or failed fetch leaves behind, or `null` while there is nothing to say. */
private fun posterFetchResultMessage(report: PosterReport?, error: String?): String? = when {
    error != null -> error
    report != null -> posterReportLine(
        fetched = report.fetched.toInt(),
        alreadyHeld = report.alreadyHeld.toInt(),
        noProviderId = report.noProviderId.toInt(),
        failed = report.failed.toInt(),
    )
    else -> null
}

/** What a fetch reported, or what stopped it — shown over whichever screen the menu action was reached from. */
@Composable
private fun PosterFetchResultDialog(message: String?, onDismiss: () -> Unit) {
    if (message == null) return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fetch posters") },
        text = { Text(message) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
