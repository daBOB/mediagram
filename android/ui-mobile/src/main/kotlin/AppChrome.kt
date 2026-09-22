package ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
 * The four things the overflow menu can do, in the order they are shown.
 *
 * Refreshing the library and fetching its details were two items and are
 * one. They were always done in that order and only in that order: a
 * refresh brings sets the channel has gained, and those are exactly the
 * sets with no synopsis and no artwork yet, so fetching without refreshing
 * first fills in gaps while leaving new ones unlisted. Two items made a
 * viewer remember a sequence the app already knew.
 *
 * It is named "Update library" and not "Refresh library", though refreshing
 * is the half anyone would name. Refreshing is a few seconds of one round
 * trip; fetching is minutes of HTTP over hundreds of titles, and a word
 * promising the first while doing the second is a lie a viewer only catches
 * by waiting.
 *
 * The TMDB key sits directly under the update it configures, and is named
 * with an ellipsis because it opens a screen to fill in rather than doing
 * anything itself.
 *
 * Updating is second and start over last, two items apart: updating is the
 * most-used of the four and starting over discards this device's Telegram
 * session, and the most frequent should not sit beside the most
 * destructive. The confirmation dialog is a backstop, not a reason to
 * invite the mis-tap.
 *
 * [updateDisabledReason] is `null` when the action is available and a
 * sentence when it is not — a run already in flight. [updateNote] is said
 * under the label while the item stays tappable: with no TMDB key the
 * refresh still works and only the artwork half is skipped, which is worth
 * doing and worth saying. An item that silently does less than its name is
 * worse than one that says what it will leave out.
 */
data class MenuActions(
    val onSystem: () -> Unit,
    /**
     * Re-read the channel's newest index, then fill in what it has no room
     * for: the descriptions nobody wrote and the artwork no index carries.
     * In that order, because the second is about what the first brought in.
     */
    val onUpdate: () -> Unit,
    val onTmdbKey: () -> Unit,
    val onStartOver: () -> Unit,
    val updateDisabledReason: String? = null,
    val updateNote: String? = null,
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
                // The masthead band is the ground; the page is what the
                // plates are tipped onto, below it. A bar lighter than the
                // sheet it sits over inverts the one relation the palette
                // names.
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
                navigationIcon = {
                    if (backLabel != null) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.semantics { contentDescription = backLabel },
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.semantics { contentDescription = "Menu" },
                    ) { Icon(imageVector = Icons.Default.MoreVert, contentDescription = null) }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text("System") },
                            onClick = { menuExpanded = false; menu.onSystem() },
                        )
                        MenuItem(
                            label = "Update library",
                            note = menu.updateDisabledReason ?: menu.updateNote,
                            enabled = menu.updateDisabledReason == null,
                            onClick = { menuExpanded = false; menu.onUpdate() },
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
        containerColor = MaterialTheme.colorScheme.surface,
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
 * An item that says something under its own label.
 *
 * Two cases, and the note reads the same in both: unavailable, where a
 * greyed row with nothing under it leaves a viewer tapping at it to find
 * out what is wrong; and available but about to do less than its name says,
 * where the note is the part it will skip.
 */
@Composable
private fun MenuItem(label: String, note: String?, enabled: Boolean, onClick: () -> Unit) {
    DropdownMenuItem(
        text = {
            Column {
                Text(label)
                note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            }
        },
        enabled = enabled,
        onClick = onClick,
    )
}
