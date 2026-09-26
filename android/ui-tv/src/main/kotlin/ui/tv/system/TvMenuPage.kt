package ui.tv.system

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.tv.material3.Text
import catalog.MenuScreen
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.MenuActions
import ui.tv.TvTextRow
import ui.tv.catalog.TvQuietLine
import ui.tv.setup.START_OVER_BODY
import ui.tv.setup.TvConfirmDialog

/**
 * The phone's overflow menu as a page: a television has no dropdown to
 * hang off a bar, so the same five items, in [MenuActions]' order and with
 * its words, stand as rows of their own. Update library says under itself
 * what the phone's item says — why it is waiting, or what it will leave
 * out — and is drawn faint while it waits.
 *
 * [restoreKey] is the row whose screen was just left, so Back from System
 * lands on System; with none, the first row takes the remote. Start over
 * asks first, in the phone's words, with Cancel under the remote.
 *
 * [onMyList]/[onContinueWatching]/[onLatest]/[onGenres] are the four
 * utilities `mastheadSplitOf` moved off the masthead's own tab row and into
 * this overflow — reachable once each, as the web's side rail offers them
 * (see `CatalogTabs.kt`'s `UtilityDestination`), appended after the phone's five
 * so every existing row keeps its place and this page's own tests of them.
 */
@Composable
internal fun TvMenuPage(
    menu: MenuActions,
    restoreKey: String?,
    onMyList: () -> Unit = {},
    onContinueWatching: () -> Unit = {},
    onLatest: () -> Unit = {},
    onGenres: () -> Unit = {},
    onBack: () -> Unit,
) {
    var askingStartOver by rememberSaveable { mutableStateOf(false) }
    val rows = remember { MenuRow.entries.associateWith { FocusRequester() } }
    val landing = MenuRow.entries.firstOrNull { it.key != null && it.key == restoreKey } ?: MenuRow.System
    LaunchedEffect(Unit) { rows.getValue(landing).requestFocus() }
    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        Text(text = "Menu", style = TvTypeScale.title)
        TvTextRow(text = "System", onClick = menu.onSystem, focusRequester = rows.getValue(MenuRow.System))
        TvTextRow(text = "Settings", onClick = menu.onSettings, focusRequester = rows.getValue(MenuRow.Settings))
        Column {
            TvTextRow(
                text = "Update library",
                onClick = menu.onUpdate,
                focusRequester = rows.getValue(MenuRow.Update),
                enabled = menu.updateDisabledReason == null,
            )
            (menu.updateDisabledReason ?: menu.updateNote)?.let { TvQuietLine(it, Modifier.padding(top = Spacing.extraSmall)) }
        }
        TvTextRow(text = "TMDB key…", onClick = menu.onTmdbKey, focusRequester = rows.getValue(MenuRow.TmdbKey))
        TvTextRow(text = "Start over", onClick = { askingStartOver = true }, focusRequester = rows.getValue(MenuRow.StartOver))
        TvTextRow(text = "My List", onClick = onMyList, focusRequester = rows.getValue(MenuRow.MyList))
        TvTextRow(text = "Continue watching", onClick = onContinueWatching, focusRequester = rows.getValue(MenuRow.ContinueWatching))
        TvTextRow(text = "Latest", onClick = onLatest, focusRequester = rows.getValue(MenuRow.Latest))
        TvTextRow(text = "Genres", onClick = onGenres, focusRequester = rows.getValue(MenuRow.Genres))
    }

    if (askingStartOver) {
        TvConfirmDialog(
            title = "Start over?",
            body = START_OVER_BODY,
            confirmLabel = "Start over",
            confirm = {
                askingStartOver = false
                menu.onStartOver()
            },
            cancel = { askingStartOver = false },
        )
    }
}

/**
 * The page's rows, and the restore key each is remembered by when it opens
 * a screen: the key screen's own name for the three that do, and none for
 * the two that never leave the page for a screen of their own.
 */
private enum class MenuRow(
    val key: String?,
) {
    System(menuRestoreKey(MenuScreen.System)),
    Settings(menuRestoreKey(MenuScreen.Settings)),
    Update(null),
    TmdbKey(menuRestoreKey(MenuScreen.TmdbKey)),
    StartOver(null),
    MyList(null),
    ContinueWatching(null),
    Latest(null),
    Genres(null),
}

/** What the menu page remembers it opened [screen] by — never a plate's id, so never mistaken for one. */
internal fun menuRestoreKey(screen: MenuScreen): String = "menu:${screen.name}"
