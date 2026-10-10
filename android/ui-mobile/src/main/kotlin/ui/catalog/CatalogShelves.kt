package ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import catalog.CatalogTab
import catalog.CatalogUiState
import catalog.HOME_POSTER_ROW_LIMIT
import catalog.KeptKind
import catalog.allSetsById
import catalog.continueWall
import catalog.everyFilm
import catalog.franchisesIn
import catalog.hasContent
import catalog.latestOf
import catalog.magazineHomeOf
import catalog.watchlistWall
import designsystem.Spacing
import ui.chrome.LocalTopChrome
import ui.chrome.pageTurn
import ui.common.catalog.DepartmentScrollStates
import uniffi.mediagram_core.TitleInfo

/**
 * One tab on screen, chosen from the bar above it — Home, then each
 * department as its own page ([DepartmentTab]), then Collections (see
 * [CollectionsScreen]). Continue and My List draw with [KeptWall], reached
 * from the rail's own rows (`ui.chrome.BrowseActions`) rather than a visible pill.
 */
@Composable
internal fun Shelves(
    state: CatalogUiState.Ready,
    fetching: Boolean,
    chosenTab: CatalogTab,
    onTabChange: (CatalogTab) -> Unit,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenList: (id: String) -> Unit,
    onCreateList: (name: String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenGenresIndex: () -> Unit,
    onOpenLatest: () -> Unit,
    onOpenMoviesPage: () -> Unit,
    onOpenFranchise: (Long) -> Unit,
    onPlayRun: (setId: String, run: List<String>) -> Unit,
    onFinish: (setId: String) -> Unit,
    onToggleWatchlist: (setId: String, listed: Boolean) -> Unit,
    homeListState: LazyListState,
    deptScroll: DepartmentScrollStates,
    now: Long,
    titleInfo: suspend (String) -> TitleInfo?,
) {
    val shelves = state.shelves
    if (!shelves.hasContent()) {
        CenteredMessage("The library is empty.")
        return
    }
    val columns = posterColumnsFor(currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass)
    // Home, and a department whose own hero drew lead art, both draw under
    // the bar — everything else, and a hero with nothing to lead with, is
    // padded clear of it, the same seam `ui.chrome.LibraryHome` reads to decide which.
    val topChrome = LocalTopChrome.current

    Column(modifier = Modifier.fillMaxSize()) {
        if (state.refreshing || fetching) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = topChrome))
        }
        state.notice?.let { notice ->
            Text(
                text = notice,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = Spacing.medium, vertical = Spacing.small).padding(top = topChrome),
            )
        }
        // Each tab is a page of its own on the web (`#/movies`, `#/continue`),
        // so choosing one turns the page. Keyed by the tab's saved key, the
        // one a restored window files its state under; a tab is already its
        // own group below, so the key costs no state. The progress line and
        // notice above belong to no page and stay put.
        key(chosenTab.key) {
            Box(Modifier.pageTurn()) {
            when (chosenTab) {
                CatalogTab.Home -> HomeScreen(
                    magazine = remember(shelves, state.watch, state.heldIds, now) {
                        magazineHomeOf(
                            shelves, state.watch, editorsChoice = state.watch.editorsChoice, now = now, heldIds = state.heldIds,
                            recentLimit = HOME_POSTER_ROW_LIMIT,
                        )
                    },
                    latest = remember(shelves) { latestOf(shelves) },
                    watch = state.watch,
                    listState = homeListState,
                    onPlay = { id -> onPlayRun(id, emptyList()) },
                    onOpenTitle = onOpenTitle,
                    onOpenCollection = onOpenCollection,
                    onToggleWatchlist = onToggleWatchlist,
                    onSeeAll = onTabChange,
                )

                is CatalogTab.Kept -> when (chosenTab.kind) {
                    KeptKind.CONTINUE -> KeptWall(KeptKind.CONTINUE, continueWall(shelves, state.watch), state.watch, columns, onOpenTitle, state.heldIds, onFinish)
                    KeptKind.WATCHLIST -> KeptWall(KeptKind.WATCHLIST, watchlistWall(shelves, state.watch), state.watch, columns, onOpenTitle, state.heldIds)
                    KeptKind.COLLECTIONS -> {
                        val movies = remember(shelves) { everyFilm(shelves) }
                        CollectionsScreen(
                            franchises = remember(movies) { franchisesIn(movies) },
                            lists = state.watch.collections,
                            setsById = remember(shelves) { allSetsById(shelves) },
                            onOpenFranchise = onOpenFranchise,
                            onOpenList = onOpenList,
                            onCreateList = onCreateList,
                            onOpenTitle = onOpenTitle,
                        )
                    }
                }

                is CatalogTab.Dept -> shelves.firstOrNull { it.department == chosenTab.department }?.let { shelf ->
                    DepartmentTab(
                        shelf = shelf,
                        state = state,
                        columns = columns,
                        deptScroll = deptScroll,
                        onOpenTitle = onOpenTitle,
                        onOpenCollection = onOpenCollection,
                        onOpenGenre = onOpenGenre,
                        onOpenGenresIndex = onOpenGenresIndex,
                        onOpenLatest = onOpenLatest,
                        onOpenMoviesPage = onOpenMoviesPage,
                        onPlay = { id -> onPlayRun(id, emptyList()) },
                        titleInfo = titleInfo,
                    )
                }
            }
            }
        }
    }
}
