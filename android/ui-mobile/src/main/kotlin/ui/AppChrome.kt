package ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import catalog.Destination
import catalog.backLabelFor
import catalog.barTitleFor
import catalog.showsSearchAction
import ui.setup.StartOverConfirmation

/**
 * Whose shelves these are, and the way to become somebody else — the bar's
 * counterpart to the web's `#who` button. Shown as the chosen name; tapping
 * it reopens [ui.ProfilePickerScreen] over whatever is on screen.
 */
data class ProfileBarState(val name: String, val onChoose: () -> Unit)

/**
 * The app's one piece of chrome: a bar with a title, a way back where the
 * destination has one, who is watching, and an overflow menu that is the
 * same five items wherever it is opened from. Every non-player screen
 * renders its content through this.
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
 * [StartOverConfirmation] is the shared dialog behind both. The menu itself
 * — the icon, the dropdown, and what each item does — is [OverflowMenu];
 * this only owns the confirmation the destructive item leads to.
 *
 * [onSearch] sits beside the profile button on every screen this renders
 * except the search screen itself — the touch equivalent of the web's own
 * search box, which sits in its header on every page rather than only on
 * the catalog's own, but an icon that reopens the screen already on
 * screen is a control with nothing left for it to do.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScaffold(
    destination: Destination,
    onBack: () -> Unit,
    menu: MenuActions,
    profile: ProfileBarState,
    browse: BrowseActions,
    onSearch: () -> Unit,
    content: @Composable () -> Unit,
) {
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
                    if (showsSearchAction(destination)) {
                        IconButton(
                            onClick = onSearch,
                            modifier = Modifier.semantics { contentDescription = "Search" },
                        ) { Icon(imageVector = Icons.Default.Search, contentDescription = null) }
                    }
                    ProfileButton(profile)
                    OverflowMenu(menu = menu, browse = browse, onAskStartOver = { askingStartOver = true })
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

@Composable
private fun ProfileButton(profile: ProfileBarState) {
    TextButton(
        onClick = profile.onChoose,
        modifier = Modifier.semantics { contentDescription = "Who's watching: ${profile.name}" },
    ) {
        Text(profile.name, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
