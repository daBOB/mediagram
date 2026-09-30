package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import catalog.MastheadSplit
import catalog.MenuScreen
import ui.RailItem
import ui.tv.LocalLibraryCovered
import ui.tv.chrome.TvChromeFocus
import ui.tv.chrome.rememberTvChromeFocus
import ui.tv.system.menuRestoreKey

/** The catalogue's restore key for "search was opened from the departments bar" — no plate, row or list is ever keyed by it. */
internal const val TvSearchEntryKey = "masthead:search"

/** The catalogue's restore key for "the trimmed menu page was opened from the bar's own ⋮" — [TvSearchEntryKey]'s counterpart. */
internal const val TvMenuEntryKey = "masthead:menu"

/** The catalogue's restore key for "Latest was opened from the rail" — the rail row itself, not a plate on the page it opens. */
internal const val TvLatestRailKey = "rail:latest"

/** The catalogue's restore key for "the Genres index was opened from the rail". */
internal const val TvGenresRailKey = "rail:genres"

/**
 * Where the departments bar's own pills and the restore-key sentinels
 * ([TvSearchEntryKey], [TvMenuEntryKey], [TvLatestRailKey], [TvGenresRailKey],
 * and Settings'/System's own `menu:*` keys once either is left) leave the
 * remote once [TvCatalogScreen] knows what is selected — split out of it so
 * that composable reads as "what shows below the bar", not also "which of
 * eight index slots that is and where Back from six different sentinels
 * sends the remote".
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
    masthead: MastheadSplit,
    selected: Int,
    shelfCount: Int,
    collectionsIndex: Int,
    continueIndex: Int,
    watchlistIndex: Int,
    ready: Boolean,
    restoreKey: String?,
    choose: (Int) -> Unit,
    onEntryRestored: () -> Unit,
): TvCatalogRestore {
    // The bar's own tab index space is departments-only: Home, the
    // shelves, then Collections at the end — Continue/Watchlist have no
    // pill of their own any more, so a viewer on either sees no pill
    // selected (`-1`, which every entry in the row simply is not).
    val selectedPill =
        when {
            selected <= shelfCount -> selected
            selected == collectionsIndex -> masthead.departments.lastIndex
            else -> -1
        }
    val railActive =
        when (selected) {
            continueIndex -> RailItem.CONTINUE_WATCHING
            watchlistIndex -> RailItem.MY_LIST
            else -> null
        }

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
    val onSelectPill = { visiblePosition: Int ->
        val index = if (visiblePosition == masthead.departments.lastIndex) collectionsIndex else visiblePosition
        if (index != selected) pillPressed = true
        choose(index)
    }

    // Read raw, not through `LocalTakesArrivalFocus`: that local is this
    // function's own output (once combined with `covered` by the caller),
    // and every sentinel below has to sit out its own consumption while
    // covered on this same signal, not on a value that depends on it.
    val covered = LocalLibraryCovered.current
    val backFromSearch = restoreKey == TvSearchEntryKey
    val backFromMenu = restoreKey == TvMenuEntryKey
    val backFromLatestRail = restoreKey == TvLatestRailKey
    val backFromGenresRail = restoreKey == TvGenresRailKey
    val backFromSettings = restoreKey == menuRestoreKey(MenuScreen.Settings)
    val backFromSystem = restoreKey == menuRestoreKey(MenuScreen.System)
    val redirectsFocus = backFromSearch || backFromMenu || backFromLatestRail || backFromGenresRail || backFromSettings || backFromSystem
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
    // screen the remote can rest on. `requestBarFocus` — not a plain
    // `requestFocus()` — marks this a deliberate arrival on the bar for
    // `TvLibraryChrome`'s own generic recovery rule, the same way every
    // sentinel below does: none of these run from a key event, so without
    // it Compose's own re-entry fallback landing on the bar right before
    // one of them runs would be indistinguishable from this app's own
    // choice to be there.
    LaunchedEffect(!ready) { if (!ready) chromeFocus.requestBarFocus(chromeFocus.menuButtonFocus) }
    // Every sentinel below is set the moment its own frame opens — Search's
    // restore key names "search" from the instant `at.openSearch()` runs,
    // not only once it closes — so each one also waits out `covered` before
    // consuming it: fired straight away, a request onto this now-inert bar
    // would be a silent no-op, and `onEntryRestored()` would forget the key
    // before Back ever reaches the frame it names, leaving nothing to send
    // the remote back to the search button, ⋮ or a rail row at all.
    LaunchedEffect(backFromSearch, ready, covered) {
        if (backFromSearch && ready && !covered) {
            chromeFocus.requestBarFocus(chromeFocus.searchFocus)
            onEntryRestored()
        }
    }
    // After the effect above that sends an empty catalogue's remote to the
    // bar, so ⋮ is where it rests rather than the viewer's own avatar.
    LaunchedEffect(backFromMenu, covered) {
        if (backFromMenu && !covered) {
            chromeFocus.requestBarFocus(chromeFocus.menuButtonFocus)
            onEntryRestored()
        }
    }
    LaunchedEffect(backFromLatestRail, ready, covered) {
        if (backFromLatestRail && ready && !covered) {
            chromeFocus.railRowFocus.getValue(RailItem.LATEST).requestFocus()
            onEntryRestored()
        }
    }
    LaunchedEffect(backFromGenresRail, ready, covered) {
        if (backFromGenresRail && ready && !covered) {
            chromeFocus.railRowFocus.getValue(RailItem.GENRES).requestFocus()
            onEntryRestored()
        }
    }
    LaunchedEffect(backFromSettings, ready, covered) {
        if (backFromSettings && ready && !covered) {
            chromeFocus.railRowFocus.getValue(RailItem.SETTINGS).requestFocus()
            onEntryRestored()
        }
    }
    LaunchedEffect(backFromSystem, ready, covered) {
        if (backFromSystem && ready && !covered) {
            chromeFocus.railRowFocus.getValue(RailItem.SYSTEM).requestFocus()
            onEntryRestored()
        }
    }

    return TvCatalogRestore(selectedPill, onSelectPill, railActive, chromeFocus, takesArrivalFocus, wallKey)
}
