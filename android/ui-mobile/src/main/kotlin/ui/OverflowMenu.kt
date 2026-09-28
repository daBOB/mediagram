package ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/**
 * The four browsing utilities web 0.62.1 keeps in its own rail-nav —
 * My List, Continue watching, Latest and Genres — moved into this menu by
 * [ui.catalog.mastheadSplitOf]'s own split (Settings, the fifth utility, was
 * already here). Reachable from anywhere, the same as the web's rail: a
 * viewer does not first have to be on the shelves to ask for Latest.
 */
data class BrowseActions(
    val onMyList: () -> Unit,
    val onContinueWatching: () -> Unit,
    val onLatest: () -> Unit,
    val onGenres: () -> Unit,
)

/**
 * The full overflow menu — the icon that opens it and the items behind it.
 * Only compact/medium pushed frames render this now: EXPANDED ones use
 * [AndroidOnlyMenu] instead, the same trimmed menu the root chrome does,
 * since [ui.chrome.LibraryRail] beside them already carries System,
 * Settings, My List, Continue watching, Latest and Genres. Narrower than
 * EXPANDED there is no rail beside a pushed frame to carry those, so this
 * keeps them. [onAskStartOver] is separate from the rest of [menu] because
 * the item it is bound to does not act immediately: the caller owns the
 * confirmation that follows, and this only asks for it.
 */
@Composable
internal fun OverflowMenu(
    menu: MenuActions,
    browse: BrowseActions,
    onAskStartOver: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    IconButton(
        onClick = { menuExpanded = true },
        modifier = Modifier.semantics { contentDescription = "Menu" },
    ) { Icon(imageVector = Icons.Default.MoreVert, contentDescription = null) }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        DropdownMenuItem(
            text = { Text("System") },
            onClick = {
                menuExpanded = false
                menu.onSystem()
            },
        )
        DropdownMenuItem(
            text = { Text("Settings") },
            onClick = {
                menuExpanded = false
                menu.onSettings()
            },
        )
        DropdownMenuItem(text = { Text("My List") }, onClick = { menuExpanded = false; browse.onMyList() })
        DropdownMenuItem(text = { Text("Continue watching") }, onClick = { menuExpanded = false; browse.onContinueWatching() })
        DropdownMenuItem(text = { Text("Latest") }, onClick = { menuExpanded = false; browse.onLatest() })
        DropdownMenuItem(text = { Text("Genres") }, onClick = { menuExpanded = false; browse.onGenres() })
        AndroidOnlyItems(menu = menu, onAskStartOver = onAskStartOver, close = { menuExpanded = false })
    }
}

/**
 * The root chrome's own ⋮, and what a pushed frame's own bar falls back to
 * on EXPANDED — the three actions that have no web counterpart at all,
 * because the web server does them itself: Update library, TMDB key…,
 * Start over. Every other item [OverflowMenu] carries — System, Settings,
 * My List, Continue watching, Latest, Genres — has its own dedicated
 * control right beside this one there: [ui.chrome.LibraryRail] on EXPANDED,
 * [ui.chrome.CompactLibraryHeader]'s icon row on the root's own compact
 * width. Shares [AndroidOnlyItems] with [OverflowMenu] rather than keeping
 * its own second copy of the same three.
 */
@Composable
internal fun AndroidOnlyMenu(
    menu: MenuActions,
    onAskStartOver: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    IconButton(
        onClick = { menuExpanded = true },
        modifier = Modifier.semantics { contentDescription = "Menu" },
    ) { Icon(imageVector = Icons.Default.MoreVert, contentDescription = null, tint = tint) }
    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
        AndroidOnlyItems(menu = menu, onAskStartOver = onAskStartOver, close = { menuExpanded = false })
    }
}

/** The three items [OverflowMenu] and [AndroidOnlyMenu] both end with — the ones the web has no counterpart for at all. [close] is called before each action, dismissing whichever of the two menus is asking for this. */
@Composable
private fun AndroidOnlyItems(
    menu: MenuActions,
    onAskStartOver: () -> Unit,
    close: () -> Unit,
) {
    menu.onPreloads?.let { onPreloads ->
        DropdownMenuItem(text = { Text("Preloads · ${menu.preloadCount}") }, onClick = { close(); onPreloads() })
    }
    MenuItem(
        label = "Update library",
        note = menu.updateDisabledReason ?: menu.updateNote,
        enabled = menu.updateDisabledReason == null,
        onClick = {
            close()
            menu.onUpdate()
        },
    )
    DropdownMenuItem(
        text = { Text("TMDB key…") },
        onClick = {
            close()
            menu.onTmdbKey()
        },
    )
    DropdownMenuItem(
        text = { Text("Start over") },
        onClick = {
            close()
            onAskStartOver()
        },
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
private fun MenuItem(
    label: String,
    note: String?,
    enabled: Boolean,
    onClick: () -> Unit,
) {
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
