package ui.tv.system

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.MenuScreen
import catalog.profileInitial
import com.mediagram.android.core.designsystem.R
import designsystem.LocalCatalogueTones
import designsystem.Spacing
import designsystem.TvType
import designsystem.tvDp
import designsystem.tvSp
import ui.common.MenuActions
import ui.common.RailItem
import ui.tv.TvIndexRow
import ui.tv.catalog.TvQuietLine
import ui.tv.chrome.RailIcon
import ui.tv.chrome.RailRowPadding
import ui.tv.currentTvLook
import ui.tv.setup.START_OVER_BODY
import ui.tv.setup.TvConfirmDialog

/**
 * What the bar's avatar opens: a panel under the avatar, over the page it was
 * opened from, in the rail's own look — who is watching and the switch to
 * another profile (the avatar's own job on the web and the tablet), then the
 * phone's overflow menu trimmed to its Android-only rows: Update library, TMDB
 * key…, Preloads when there is anything to find there, and Start over apart at
 * the foot, the tablet's `AndroidOnlyMenu`. This television's rail carries My
 * List, Continue, Latest, Genres, Stats, Settings and System, so the panel
 * offers them only in a style that draws no rail ([railItems] non-empty).
 * The web has no such menu: its avatar opens Who's watching, and none of these
 * rows exist there.
 *
 * Update library says under itself why it waits or what it will leave out,
 * and is drawn faint while it waits. Start over asks first, Cancel under the
 * remote. The remote cannot leave the panel sideways or past its ends; Back
 * ([onBack]) closes it, and the chrome then hands the remote to the avatar.
 * [restoreKey] lands it on the rail row whose screen was just left.
 */
@Composable
internal fun TvMenuPage(
    menu: MenuActions,
    restoreKey: String?,
    watchingAs: String,
    onSwitchProfile: () -> Unit,
    modifier: Modifier = Modifier,
    railItems: List<RailItem> = emptyList(),
    onRailItem: (RailItem) -> Unit = {},
    onBack: () -> Unit,
) {
    var askingStartOver by rememberSaveable { mutableStateOf(false) }
    val switchProfile = remember { FocusRequester() }
    val fixedRows = remember { List(4) { FocusRequester() } }
    val railRows = remember { RailItem.entries.associateWith { FocusRequester() } }
    // Back from Settings or System — opened from here in a style with no rail — lands on its own row.
    val railLanding =
        when (restoreKey) {
            menuRestoreKey(MenuScreen.Settings) -> RailItem.SETTINGS
            menuRestoreKey(MenuScreen.System) -> RailItem.SYSTEM
            else -> null
        }?.takeIf { it in railItems }
    LaunchedEffect(Unit) { (railLanding?.let(railRows::getValue) ?: switchProfile).requestFocus() }
    BackHandler(onBack = onBack)

    val rows =
        buildList {
            add(MenuEntry("Switch profile", R.drawable.core_designsystem_ic_settings_profile, onSwitchProfile, switchProfile))
            for (item in railItems) add(MenuEntry(item.label, item.icon, { onRailItem(item) }, railRows.getValue(item), groupStart = item == railItems.first()))
            add(
                MenuEntry(
                    "Update library", R.drawable.core_designsystem_ic_menu_update, menu.onUpdate, fixedRows[0], groupStart = true,
                    enabled = menu.updateDisabledReason == null, status = menu.updateDisabledReason ?: menu.updateNote,
                ),
            )
            add(MenuEntry("TMDB key…", R.drawable.core_designsystem_ic_menu_key, menu.onTmdbKey, fixedRows[1]))
            menu.onPreloads?.let { add(MenuEntry("Preloads", R.drawable.core_designsystem_ic_settings_storage, it, fixedRows[2], trailing = menu.preloadCount.takeIf { n -> n > 0 }?.toString())) }
            add(MenuEntry("Start over", R.drawable.core_designsystem_ic_menu_start_over, { askingStartOver = true }, fixedRows[3], groupStart = true))
        }

    val tones = LocalCatalogueTones.current
    val shape = currentTvLook.cardShape
    Column(
        modifier =
            modifier
                .width(PanelWidth)
                .clip(shape)
                .background(tones.sidebar)
                .border(1.dp, tones.ruleSoft, shape)
                .verticalScroll(rememberScrollState())
                .padding(PanelPadding),
    ) {
        WhoIsWatching(watchingAs)
        rows.forEachIndexed { index, entry ->
            if (entry.groupStart) Box(Modifier.padding(vertical = 8.tvDp).fillMaxWidth().height(1.dp).background(tones.ruleSoft))
            TvIndexRow(
                icon = painterResource(entry.icon),
                label = entry.label,
                selected = false,
                focusRequester = entry.focus,
                onSelect = { if (entry.enabled) entry.onSelect() },
                modifier =
                    Modifier
                        .then(if (entry.enabled) Modifier else Modifier.alpha(DisabledAlpha))
                        .focusProperties {
                            left = FocusRequester.Cancel
                            right = FocusRequester.Cancel
                            if (index == 0) up = FocusRequester.Cancel
                            if (index == rows.lastIndex) down = FocusRequester.Cancel
                        },
                minHeight = 44.tvDp,
                contentPadding = RailRowPadding,
                iconSize = RailIcon,
                iconGap = 12.tvDp,
                alignment = Alignment.CenterVertically,
                trailing = entry.trailing,
                status = entry.status?.let { { TvQuietLine(it, Modifier.padding(top = 2.tvDp)) } },
            )
        }
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

/** The panel's head: the avatar's own initial in its circle, and who is watching under an eyebrow. */
@Composable
private fun WhoIsWatching(name: String) {
    Row(
        modifier = Modifier.padding(start = 12.tvDp, end = 12.tvDp, top = 6.tvDp, bottom = 14.tvDp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.tvDp),
    ) {
        Box(Modifier.size(44.tvDp).background(MaterialTheme.colorScheme.onBackground, CircleShape), contentAlignment = Alignment.Center) {
            Text(text = profileInitial(name), style = TvType.labelLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 17.tvSp), color = MaterialTheme.colorScheme.background)
        }
        Column {
            Text(text = "WATCHING AS", style = TvType.labelSmall.copy(fontSize = 10.tvSp, letterSpacing = 0.28.em), color = LocalCatalogueTones.current.quiet)
            Text(
                text = name,
                style = TvType.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 22.tvSp, letterSpacing = (-0.02).em),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** One row of the panel; [groupStart] draws a rule above it, setting it apart from the rows before. */
private class MenuEntry(
    val label: String,
    val icon: Int,
    val onSelect: () -> Unit,
    val focus: FocusRequester,
    val groupStart: Boolean = false,
    val enabled: Boolean = true,
    val status: String? = null,
    val trailing: String? = null,
)

/** The open rail's own width and a little more, so "Update library" and its note never wrap. */
private val PanelWidth = 300.tvDp

/** `.library-rail`'s inset (`shell.css:24`), at the panel's ends too. */
private val PanelPadding = 18.tvDp

/** How faint Update library is drawn while it cannot run — readable across a room, plainly not on offer. */
private const val DisabledAlpha = 0.5f

/** What the menu remembers it opened [screen] by — never a plate's id, so never mistaken for one. */
internal fun menuRestoreKey(screen: MenuScreen): String = "menu:${screen.name}"
