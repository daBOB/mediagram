package ui.tv

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import catalog.CatalogTab
import catalog.CatalogUiState
import catalog.ChromeCounts
import catalog.Department
import catalog.Entry
import catalog.KeptKind
import catalog.Shelf
import catalog.chromeCountsOf
import catalog.hasContent
import catalog.libraryTallyLines
import catalog.mastheadTabsOf
import stats.AchievementDotViewModel
import ui.common.FrameKind
import ui.common.RailItem
import ui.tv.chrome.LocalNewAchievement
import ui.tv.chrome.TvLibraryChrome
import ui.tv.chrome.rememberTvChromeFocus
import ui.tv.profile.TvChosenProfile

/**
 * What a page opened over the catalogue lights in the chrome: the web's
 * `sectionOf` (`address.js:122`). A show or course lights its department's
 * pill, the Movies department's full wall Movies, a franchise or a list
 * Collections; Latest, Genres and Stats light their own rail row. Anything
 * else — a film, a person, a genre, search — lights nothing, as the web's
 * `title`, `person`, `genre` and `search` name no link.
 */
internal data class TvFrameSection(
    val tab: CatalogTab? = null,
    val rail: RailItem? = null,
)

internal fun tvFrameSection(
    top: FrameKind,
    collection: Entry.Collection?,
    shelves: List<Shelf>,
): TvFrameSection =
    when (top) {
        FrameKind.COLLECTION -> {
            val shelf = collection?.let { shown -> shelves.firstOrNull { s -> s.entries.any { it is Entry.Collection && it.key == shown.key } } }
            TvFrameSection(tab = shelf?.let { CatalogTab.Dept(it.department) })
        }
        FrameKind.MOVIES_PAGE -> TvFrameSection(tab = CatalogTab.Dept(Department.MOVIES))
        FrameKind.FRANCHISE, FrameKind.LIST -> TvFrameSection(tab = CatalogTab.Kept(KeptKind.COLLECTIONS))
        FrameKind.LATEST -> TvFrameSection(rail = RailItem.LATEST)
        FrameKind.GENRES -> TvFrameSection(rail = RailItem.GENRES)
        FrameKind.STATS -> TvFrameSection(rail = RailItem.STATS)
        else -> TvFrameSection()
    }

/**
 * The web's shell around a page opened over the catalogue (`index.html:51,74,119`:
 * the rail and the masthead frame every page, not only the front ones) — the
 * same [TvLibraryChrome] the catalogue draws, so a title, a show or a genre
 * starts where Home does and the rail and the bar stay where they were.
 *
 * [onPill] and [onRail] go back to the catalogue and run there, as a link in
 * the web's shell leaves the page it is on; [onSearch] and [onMenu] open over
 * this page. Unlike the web's, the stack under the page goes with it: Back
 * from the department a pill opened goes to its pill and the rail, the
 * catalogue's own chain, not to the page — a remote has no history to walk. Back closes the page from anywhere in it — the page, the bar or
 * the rail — through the frame's own `BackHandler`, so the catalogue's own
 * Back chain is off here.
 */
@Composable
internal fun TvFrameChrome(
    ready: CatalogUiState.Ready?,
    lit: TvFrameSection,
    profile: TvChosenProfile,
    onPill: (CatalogTab) -> Unit,
    onRail: (RailItem) -> Unit,
    onSearch: () -> Unit,
    onMenu: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shelves = ready?.shelves?.takeIf { it.hasContent() }.orEmpty()
    val tabs = remember(shelves) { if (shelves.isEmpty()) emptyList() else mastheadTabsOf(shelves) }
    val counts = remember(shelves, ready?.watch) { ready?.let { chromeCountsOf(shelves, it.watch) } ?: ChromeCounts.Empty }
    val tally = remember(shelves) { libraryTallyLines(shelves) }
    val achievementDot: AchievementDotViewModel = hiltViewModel()
    val newAchievement by achievementDot.newAchievement.collectAsStateWithLifecycle()
    CompositionLocalProvider(LocalNewAchievement provides newAchievement) {
        TvLibraryChrome(
            pills = remember(tabs) { tabs.map { it.label } },
            selectedPill = lit.tab?.let(tabs::indexOf) ?: -1,
            onSelectPill = { onPill(tabs[it]) },
            railActive = lit.rail,
            counts = counts,
            tally = tally,
            onRailSelect = onRail,
            onSearch = onSearch,
            profile = profile,
            onMenu = onMenu,
            focus = rememberTvChromeFocus(),
            modifier = Modifier.fillMaxSize(),
            backChain = false,
        ) {
            // One focus group for the page: the chrome's exit rule (Up to the bar, Left to the rail)
            // binds to the outermost focus targets under it, and without this a page's own inner
            // group — search's results, a title's tab row — sent Up to the bar instead of the field
            // or the pills above it.
            // shortcut: the rail's Right lands on the page's first composed stop, not the one it left; a
            // restorer over the page also answers the page's own arrival on the box — route explicitly if missed.
            Box(Modifier.fillMaxSize().focusGroup()) { content() }
        }
    }
}
