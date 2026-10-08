package ui.tv.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import catalog.Department
import catalog.MoviesDepartment
import catalog.moviesLineOf
import designsystem.Spacing
import kotlinx.coroutines.flow.first as firstOf
import ui.tv.catalog.home.TvBandHeading
import ui.tv.chrome.LocalTvPagePadding
import ui.tv.rememberStableRequester

/** How far past the page's own viewport a section stays composed — one section further either way, generous enough on a page this short. */
private val MoviesCacheWindow = 900.dp

/** For a test to tell this page's own outer list apart from every row's own inner one, both scrollable. */
internal const val TvMoviesDepartmentPageTestTag = "tv-movies-department-page"

/**
 * The Movies department's own front page — the television twin of the
 * phone's department screen and the web's `department-pages.js#renderMoviesDept`:
 * [TvDepartmentHero], then Featured, Genres, Acclaimed and Recently added,
 * and a pill down to every film the shelf holds. `null` [dept] (an empty
 * Movies shelf) is the caller's own concern — [DepartmentOrShelfWall] never
 * reaches this composable for one.
 *
 * A `LazyColumn`, not the `Column` + `verticalScroll` this page once was:
 * only the section near the viewport is ever composed. Arrival or a
 * [restoreKey] scrolls this list to the target section's own item first, and
 * waits for it to actually be laid out, before that section's own row asks
 * for its plate's focus — [ui.tv.catalog.home.TvHome]'s own doc on why a
 * blind request the instant a lazy section mounts is not enough once the
 * *outer* list is lazy too.
 */
@Composable
internal fun TvMoviesDepartmentPage(
    dept: MoviesDepartment,
    onOpenTitle: (setId: String) -> Unit,
    onPlay: (setId: String) -> Unit,
    onOpenGenre: (name: String) -> Unit,
    onOpenAllFilms: () -> Unit,
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
    listState: LazyListState = rememberLazyListState(cacheWindow = LazyLayoutCacheWindow(ahead = MoviesCacheWindow, behind = MoviesCacheWindow)),
) {
    val focus = remember { FocusRequester() }
    // The band the remote was last in, the same rule Home keeps: a film
    // Featured also carries in Recently added (unlikely, but not excluded
    // the way the lead itself is) comes back to the row it was opened from.
    var lastSection by rememberSaveable { mutableStateOf<MoviesSection?>(null) }
    val target = remember(dept, restoreKey, lastSection) { moviesDeptTargetOf(dept, restoreKey, lastSection) }
    // Exactly the rows the list below draws, in order, so the arrival's scroll index lands on its row.
    val included = remember(dept) { moviesSections(dept).filter { it.stops.isNotEmpty() }.map { it.id } + MoviesSection.ALL }
    val takesFocus = LocalTakesArrivalFocus.current
    var sectionInView by remember { mutableStateOf(false) }
    LaunchedEffect(target, takesFocus) {
        sectionInView = false
        if (!takesFocus) return@LaunchedEffect
        val itemIndex = included.indexOf(target.section) + 1 // the hero is item 0.
        listState.scrollToItem(itemIndex)
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.firstOf { info -> info.any { it.index == itemIndex } }
        sectionInView = true
    }
    // The "all films" pill is not a row of its own with a scroll to wait on
    // — once the outer list above has it on screen, its own `focusRequester`
    // is already attached and ready.
    LaunchedEffect(sectionInView, target) { if (sectionInView && target.section == MoviesSection.ALL) focus.requestFocus() }
    val rowTakesFocus = takesFocus && sectionInView

    val pagePadding = LocalTvPagePadding.current
    TvPage(takesArrivalFocus = takesFocus) {
        LazyColumn(
            state = listState,
            // Scoped to this list alone — the rail's Right, a blind entry
            // naming no row of its own, is what this is for; every
            // restore-key arrival above already requests a named plate
            // directly, which never defers to this restorer's own search.
            modifier = Modifier.fillMaxSize().testTag(TvMoviesDepartmentPageTestTag).toTopWhenLeftForTheBar { listState.animateScrollToItem(0) }.focusRestorer(fallback = focus),
            contentPadding = PaddingValues(top = pagePadding.top, bottom = pagePadding.bottom),
        ) {
            item(key = "hero") {
                Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                    TvDepartmentHero(title = Department.MOVIES.label, line = moviesLineOf(dept), lead = dept.lead)
                }
            }
            if (MoviesSection.FEATURED in included) {
                item(key = MoviesSection.FEATURED) {
                    Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                        DeptRow(
                            "Featured",
                            dept.featured,
                            onOpenTitle,
                            focusAt = target.stopAt(MoviesSection.FEATURED),
                            focus = focus,
                            takesFocus = rowTakesFocus,
                            heldIds = heldIds,
                            onSectionFocused = { lastSection = MoviesSection.FEATURED },
                        )
                    }
                }
            }
            if (MoviesSection.GENRES in included) {
                item(key = MoviesSection.GENRES) {
                    Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                        Box(Modifier.padding(top = Spacing.large)) { TvBandHeading(title = "Genres", count = null) }
                        GenreTileRow(
                            dept.genres,
                            onOpenGenre,
                            focusAt = target.stopAt(MoviesSection.GENRES),
                            focus = focus,
                            takesFocus = rowTakesFocus,
                            onSectionFocused = { lastSection = MoviesSection.GENRES },
                        )
                    }
                }
            }
            if (MoviesSection.ACCLAIMED in included) {
                item(key = MoviesSection.ACCLAIMED) {
                    Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                        DeptRow(
                            "Acclaimed, not yet seen",
                            dept.acclaimed,
                            onOpenTitle,
                            focusAt = target.stopAt(MoviesSection.ACCLAIMED),
                            focus = focus,
                            takesFocus = rowTakesFocus,
                            heldIds = heldIds,
                            onSectionFocused = { lastSection = MoviesSection.ACCLAIMED },
                        )
                    }
                }
            }
            if (MoviesSection.RECENTLY_ADDED in included) {
                item(key = MoviesSection.RECENTLY_ADDED) {
                    Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                        DeptRow(
                            "Recently added",
                            dept.recentlyAdded,
                            onOpenTitle,
                            focusAt = target.stopAt(MoviesSection.RECENTLY_ADDED),
                            focus = focus,
                            takesFocus = rowTakesFocus,
                            heldIds = heldIds,
                            onSectionFocused = { lastSection = MoviesSection.RECENTLY_ADDED },
                        )
                    }
                }
            }
            item(key = MoviesSection.ALL) {
                // The web's `.dept-all` and the phone's `PagePill`: a round
                // outline link at the page's foot, 56dp under the last row.
                Box(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end, top = 56.dp, bottom = Spacing.medium)) {
                    TvPagePill(
                        text = "All ${dept.filmCount} films →",
                        onClick = onOpenAllFilms,
                        modifier = Modifier.focusRequester(rememberStableRequester(focus.takeIf { target.section == MoviesSection.ALL })),
                    )
                }
            }
        }
    }
}
