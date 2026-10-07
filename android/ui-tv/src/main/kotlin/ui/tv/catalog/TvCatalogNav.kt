package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import catalog.CatalogTab
import catalog.MenuScreen
import ui.RailItem
import ui.railItemOf
import ui.tv.chrome.TvChromeFocus
import ui.tv.chrome.rememberTvChromeFocus
import ui.tv.system.menuRestoreKey

/** The catalogue's restore key for "search was opened from the departments bar" — no plate, row or list is ever keyed by it. */
internal const val TvSearchEntryKey = "masthead:search"

/** The catalogue's restore key for "the trimmed menu page was opened from the bar's own ⋮" — [TvSearchEntryKey]'s counterpart. */
internal const val TvMenuEntryKey = "masthead:menu"

/** The catalogue's restore key for ""All N films" was opened from the Movies department" — no plate of its own to remember instead. */
internal const val TvMoviesPageEntryKey = "movies:all"

/** The catalogue's restore key for "Latest was opened from the rail" — the rail row itself, not a plate on the page it opens. */
internal const val TvLatestRailKey = "rail:latest"

/** The catalogue's restore key for "the Genres index was opened from the rail". */
internal const val TvGenresRailKey = "rail:genres"

/** The catalogue's restore key for "Stats was opened from the rail". */
internal const val TvStatsRailKey = "rail:stats"

/**
 * Where the departments bar's own pills and the restore-key sentinels
 * ([TvSearchEntryKey], [TvMenuEntryKey], [TvLatestRailKey], [TvGenresRailKey],
 * [TvStatsRailKey], and Settings'/System's own `menu:*` keys once either is
 * left) leave the remote once [TvCatalogScreen] knows what is selected —
 * split out of it so that composable reads as "what shows below the bar",
 * not also "which pill that is and where Back from seven different
 * sentinels sends the remote".
 */
internal class TvCatalogRestore(
    val selectedPill: Int,
    val onSelectPill: (Int) -> Unit,
    val railActive: RailItem?,
    val chromeFocus: TvChromeFocus,
    val takesArrivalFocus: Boolean,
    val wallKey: String?,
)

@Composable
internal fun rememberTvCatalogRestore(
    mastheadTabs: List<CatalogTab>,
    selected: CatalogTab,
    ready: Boolean,
    restoreKey: String?,
    choose: (CatalogTab) -> Unit,
    onEntryRestored: () -> Unit,
): TvCatalogRestore {
    // Continue and My List have no pill of their own, so a viewer on
    // either sees no pill selected (`-1`, which every entry in the row
    // simply is not) and that row of the rail active instead.
    val selectedPill = mastheadTabs.indexOf(selected)
    val railActive = railItemOf(selected)

    val chromeFocus = rememberTvChromeFocus()

    // A pill pressed with the remote kept on it, per the plan's own
    // "Pill OK: selects the department, the remote stays on the pill" —
    // arrival focus into the page it just switched to is suppressed until
    // either Down is pressed (which does not touch this flag: it moves the
    // remote directly, `TvDepartmentsBar`'s own `downTarget`) or something
    // on that page is opened and left again, which is exactly what turns
    // `wallKey` non-null below. Saveable so a rotation mid-press does not
    // suddenly let arrival focus back in on a page the viewer deliberately
    // left the remote above.
    var pillPressed by rememberSaveable { mutableStateOf(false) }
    val onSelectPill = { position: Int ->
        val tab = mastheadTabs[position]
        if (tab != selected) pillPressed = true
        choose(tab)
    }

    val backFromSearch = restoreKey == TvSearchEntryKey
    val backFromMenu = restoreKey == TvMenuEntryKey
    // The rail row a page opened from the rail hands the remote back to.
    val railTarget =
        when (restoreKey) {
            TvLatestRailKey -> RailItem.LATEST
            TvGenresRailKey -> RailItem.GENRES
            TvStatsRailKey -> RailItem.STATS
            menuRestoreKey(MenuScreen.Settings) -> RailItem.SETTINGS
            menuRestoreKey(MenuScreen.System) -> RailItem.SYSTEM
            else -> null
        }
    val redirectsFocus = backFromSearch || backFromMenu || railTarget != null
    val wallKey = restoreKey.takeUnless { redirectsFocus }
    // Content never takes arrival focus while a sentinel is sending the
    // remote to one specific bar or rail control instead (`wallKey` is
    // already null in every one of those cases) — without this, content's
    // own arrival effect would win the same race and pull the remote onto
    // a plate the instant the search field or a settings screen is left.
    // Once `wallKey` itself turns non-null (something was opened and left),
    // arrival is allowed again even with `pillPressed` still true from the
    // press that got here.
    val takesArrivalFocus = wallKey != null || (!pillPressed && !redirectsFocus)

    // With no wall below to take focus, the bar is the one thing on
    // screen the remote can rest on.
    LaunchedEffect(!ready) { if (!ready) chromeFocus.menuButtonFocus.requestFocus() }
    LaunchedEffect(backFromSearch, ready) {
        if (backFromSearch && ready) {
            chromeFocus.searchFocus.requestFocus()
            onEntryRestored()
        }
    }
    // After the effect above that sends an empty catalogue's remote to the
    // bar, so ⋮ is where it rests rather than the viewer's own avatar.
    LaunchedEffect(backFromMenu) {
        if (backFromMenu) {
            chromeFocus.menuButtonFocus.requestFocus()
            onEntryRestored()
        }
    }
    LaunchedEffect(railTarget, ready) {
        if (railTarget != null && ready) {
            chromeFocus.railRowFocus.getValue(railTarget).requestFocus()
            onEntryRestored()
        }
    }

    return TvCatalogRestore(selectedPill, onSelectPill, railActive, chromeFocus, takesArrivalFocus, wallKey)
}
