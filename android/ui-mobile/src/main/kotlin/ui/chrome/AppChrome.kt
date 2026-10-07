package ui.chrome

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.window.core.layout.WindowWidthSizeClass
import ui.Destination
import ui.backLabelFor
import ui.barTitleFor
import ui.showsSearchAction
import ui.MenuActions
import ui.RailItem
import ui.pageGround
import ui.setup.StartOverConfirmation

/**
 * Whose shelves these are, and the way to become somebody else — the bar's
 * counterpart to the web's `#who` button. Shown as the chosen name; tapping
 * it reopens [ui.profile.ProfilePickerScreen] over whatever is on screen.
 */
data class ProfileBarState(val name: String, val onChoose: () -> Unit)

/**
 * A pushed frame's own bar: a title, a way back where the destination has
 * one, who is watching, and an overflow menu — trimmed to the three
 * Android-only actions (Update library, TMDB key…, Start over) on EXPANDED,
 * where [LibraryRail] beside it already carries My List, Continue,
 * Latest, Genres, Stats, Settings and System; the full menu everywhere narrower,
 * where there is no rail to carry them. Every pushed frame renders through
 * this except Settings and System, which have their own index pane instead
 * of a bar at all; the root catalog renders through [ui.chrome.LibraryHome],
 * not this; the player fills the window with neither.
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
 *
 * [onSearch] sits beside the profile button on every screen this renders
 * except the search screen itself — the touch equivalent of the web's own
 * search box, which sits in its header on every page rather than only on
 * the catalog's own, but an icon that reopens the screen already on
 * screen is a control with nothing left for it to do.
 *
 * [content] has exactly one call site below — see [ui.chrome.LibraryHome]'s
 * own note on why: a width-class change must not move it to a different
 * slot in the composition, or every `remember`/`rememberSaveable` under it
 * is lost with it.
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
    val expanded = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
    val rail = LocalRailData.current

    Row(Modifier.fillMaxSize()) {
        if (expanded) {
            LibraryRail(active = railItemFor(destination), onHome = rail.onHome, onSelect = { railSelect(it, browse, menu) }, modifier = Modifier.fillMaxHeight())
        }
        Box(Modifier.weight(1f).fillMaxHeight()) {
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
                            if (expanded) {
                                ChromeAvatar(profile = profile)
                                AndroidOnlyMenu(menu = menu, onAskStartOver = { askingStartOver = true })
                            } else {
                                ProfileButton(profile)
                                OverflowMenu(menu = menu, browse = browse, onAskStartOver = { askingStartOver = true })
                            }
                        },
                    )
                },
                // The page, not a plate on it — [pageGround], matching
                // `MobileApp`'s own root `Surface` and every hero's own
                // scrim, which all fade into this same ground.
                containerColor = MaterialTheme.colorScheme.pageGround,
            ) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) { content() }
            }
        }
    }

    StartOverConfirmation(
        asking = askingStartOver,
        onDismiss = { askingStartOver = false },
        onConfirm = menu.onStartOver,
    )
}

/** Which rail row, if any, is where [destination] already is — Latest, Genres and Stats are the only pushed frames the rail also names; My List/Continue/Settings/System resolve to their own root tab or their own frame, never this one. */
private fun railItemFor(destination: Destination): RailItem? =
    when (destination) {
        Destination.Latest -> RailItem.LATEST
        Destination.Genres -> RailItem.GENRES
        Destination.Stats -> RailItem.STATS
        else -> null
    }

/** A rail tap's own effect, the same wherever the rail renders — [ui.chrome.LibraryHome] reuses this rather than repeating the same seven-way branch. */
internal fun railSelect(
    item: RailItem,
    browse: BrowseActions,
    menu: MenuActions,
) {
    when (item) {
        RailItem.MY_LIST -> browse.onMyList()
        RailItem.CONTINUE_WATCHING -> browse.onContinueWatching()
        RailItem.LATEST -> browse.onLatest()
        RailItem.GENRES -> browse.onGenres()
        RailItem.STATS -> browse.onStats()
        RailItem.SETTINGS -> menu.onSettings()
        RailItem.SYSTEM -> menu.onSystem()
    }
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
