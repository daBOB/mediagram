package ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

/**
 * Where in the library a viewer currently is. The catalog is the root; a
 * collection is named after whatever course or show it opened; the system
 * screen sits alongside both rather than under either.
 */
sealed interface Destination {
    data object Catalog : Destination
    data class Collection(val name: String) : Destination
    data object System : Destination
}

/**
 * What the bar says it is showing. Read from the destination rather than
 * passed in by each screen, so the catalog, a collection and the system
 * screen cannot each spell their own title differently.
 */
internal fun barTitleFor(destination: Destination): String = when (destination) {
    Destination.Catalog -> "Mediagram"
    is Destination.Collection -> destination.name
    Destination.System -> "System"
}

/**
 * Whether the bar offers a way back, and what it is called. The catalog is
 * the top of the tree; a back arrow there would either do nothing or leave
 * the app, and both are worse than no arrow.
 */
internal fun backLabelFor(destination: Destination): String? = when (destination) {
    Destination.Catalog -> null
    is Destination.Collection -> "Back"
    Destination.System -> "Back"
}

/**
 * The four things the overflow menu can do. Two of them — fetch posters and
 * the TMDB key — are named with an ellipsis because they open something
 * rather than doing it outright, which is the difference between a menu
 * item and a button that starts a network run without warning.
 */
data class MenuActions(
    val onSystem: () -> Unit,
    val onFetchPosters: () -> Unit,
    val onTmdbKey: () -> Unit,
    val onStartOver: () -> Unit,
)

/**
 * The app's one piece of chrome: a bar with a title, an optional way back,
 * and an overflow menu that is the same four items wherever it is opened
 * from. Every non-player screen renders its content through this.
 *
 * Start over asks the same confirmation [StartOverAction] always has —
 * [StartOverConfirmation] is the shared dialog behind both.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScaffold(
    title: String,
    onBack: (() -> Unit)?,
    menu: MenuActions,
    content: @Composable () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var askingStartOver by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    if (onBack != null) {
                        IconButton(onClick = onBack) { Text("←") }
                    }
                },
                actions = {
                    IconButton(onClick = { menuExpanded = true }) { Text("⋮") }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("System") },
                            onClick = { menuExpanded = false; menu.onSystem() },
                        )
                        DropdownMenuItem(
                            text = { Text("Fetch posters…") },
                            onClick = { menuExpanded = false; menu.onFetchPosters() },
                        )
                        DropdownMenuItem(
                            text = { Text("TMDB key…") },
                            onClick = { menuExpanded = false; menu.onTmdbKey() },
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
