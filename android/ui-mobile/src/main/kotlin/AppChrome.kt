package ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * Where in the library a viewer currently is. The catalog is the root; a
 * collection is named after whatever course or show it opened; a title is
 * named after itself, and is reachable from either; the system screen sits
 * alongside all of them rather than under any.
 */
sealed interface Destination {
    data object Catalog : Destination
    data class Collection(val name: String) : Destination
    data class Title(val name: String) : Destination
    data object System : Destination
    data object TmdbKey : Destination
}

/**
 * What the bar says it is showing. Read from the destination rather than
 * passed in by each screen, so the catalog, a collection and the system
 * screen cannot each spell their own title differently.
 */
internal fun barTitleFor(destination: Destination): String = when (destination) {
    Destination.Catalog -> "Mediagram"
    is Destination.Collection -> destination.name
    is Destination.Title -> destination.name
    Destination.System -> "System"
    Destination.TmdbKey -> "TMDB key"
}

/**
 * Whether the bar offers a way back, and what it is called. The catalog is
 * the top of the tree; a back arrow there would either do nothing or leave
 * the app, and both are worse than no arrow.
 */
internal fun backLabelFor(destination: Destination): String? = when (destination) {
    Destination.Catalog -> null
    is Destination.Collection -> "Back"
    is Destination.Title -> "Back"
    Destination.System -> "Back"
    Destination.TmdbKey -> "Back"
}

/**
 * The five things the overflow menu can do. Two of them — fetch posters and
 * the TMDB key — are named with an ellipsis because they open something
 * rather than doing it outright, which is the difference between a menu
 * item and a button that starts a network run without warning. Refreshing
 * the library has none, because it does the thing.
 *
 * The two disabled reasons are `null` when their action is available and a
 * sentence when it is not — no key stored, or a run already in flight. An
 * item that silently does nothing is worse than one that says why it
 * cannot, so the reason is shown, not just the disabled state.
 */
data class MenuActions(
    val onSystem: () -> Unit,
    val onFetchPosters: () -> Unit,
    val onTmdbKey: () -> Unit,
    val onRefresh: () -> Unit,
    val onStartOver: () -> Unit,
    val fetchPostersDisabledReason: String? = null,
    val refreshDisabledReason: String? = null,
)

/**
 * The app's one piece of chrome: a bar with a title, a way back where the
 * destination has one, and an overflow menu that is the same five items
 * wherever it is opened from. Every non-player screen renders its content
 * through this.
 *
 * The title and the back affordance are both derived from [destination]
 * here, in one place, rather than handed in already decided — [barTitleFor]
 * and [backLabelFor] are the decision, and this is the only caller either
 * needs. [onBack] is still supplied by the caller because only the caller
 * knows what "back" means for it (clear a saved id, in every case so far);
 * whether that lambda is ever reachable is [backLabelFor]'s call, not the
 * caller's.
 *
 * Start over asks the same confirmation [StartOverAction] always has —
 * [StartOverConfirmation] is the shared dialog behind both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScaffold(
    destination: Destination,
    onBack: () -> Unit,
    menu: MenuActions,
    content: @Composable () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var askingStartOver by remember { mutableStateOf(false) }
    val backLabel = backLabelFor(destination)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(barTitleFor(destination)) },
                navigationIcon = {
                    if (backLabel != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.semantics { contentDescription = backLabel },
                        ) { Text("←") }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.semantics { contentDescription = "Menu" },
                    ) { Text("⋮") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("System") },
                            onClick = { menuExpanded = false; menu.onSystem() },
                        )
                        MenuItem(
                            label = "Fetch posters…",
                            disabledReason = menu.fetchPostersDisabledReason,
                            onClick = { menuExpanded = false; menu.onFetchPosters() },
                        )
                        DropdownMenuItem(
                            text = { Text("TMDB key…") },
                            onClick = { menuExpanded = false; menu.onTmdbKey() },
                        )
                        MenuItem(
                            label = "Refresh library",
                            disabledReason = menu.refreshDisabledReason,
                            onClick = { menuExpanded = false; menu.onRefresh() },
                        )
                        DropdownMenuItem(
                            text = { Text("Start over") },
                            onClick = { menuExpanded = false; askingStartOver = true },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) { content() }
    }

    StartOverConfirmation(
        asking = askingStartOver,
        onDismiss = { askingStartOver = false },
        onConfirm = menu.onStartOver,
    )
}

/**
 * An item that can be unavailable, and says why underneath its own label
 * when it is. A greyed row with nothing under it leaves a viewer tapping at
 * it to find out what is wrong.
 */
@Composable
private fun MenuItem(label: String, disabledReason: String?, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(label)
                disabledReason?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        enabled = disabledReason == null,
        onClick = onClick,
    )
}
