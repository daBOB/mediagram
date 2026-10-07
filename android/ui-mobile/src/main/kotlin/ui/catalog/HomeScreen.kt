package ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import catalog.CatalogTab
import catalog.Department
import catalog.KeptKind
import catalog.Latest
import catalog.MagazineHome
import model.WatchSnapshot
import ui.catalog.home.BandHeading
import ui.catalog.home.CompactBreakpoint
import ui.catalog.home.ContinueBand
import ui.catalog.home.CourseList
import ui.catalog.home.HomeCover
import ui.catalog.home.HomeFeatures
import ui.catalog.home.HomeShelfRow
import ui.catalog.home.RecentBand
import ui.catalog.home.gutterFor

/**
 * The magazine home page: cover story, features, Continue watching beside a
 * pull-quote, what arrived beside This month, then Latest series and Latest
 * courses — a Compose port of `home-view.js`'s own section order. A part
 * with nothing in it is not drawn at all, the same rule the web follows.
 *
 * A `LazyColumn` of full-width sections, not the poster grid-in-grid the
 * plain shelves use: every row here is its own horizontal strip or list,
 * chosen by what it shows rather than forced into one grid's column count.
 *
 * [latest] gives the two bands that follow the magazine header — Latest
 * series and Latest courses; [magazine] already carries the cover,
 * features, resume strip and its own "Recently added" row. [onSeeAll] opens
 * the tab a band is a window onto.
 *
 * [listState] is hoisted up to [ui.chrome.LibraryHome] rather than kept as
 * this screen's own — the departments bar over the cover reads where the
 * page actually is from the same instance, rather than tracking scroll
 * deltas of its own that a restored position or a scroll-up-from-deep would
 * leave out of step with.
 */
@Composable
internal fun HomeScreen(
    magazine: MagazineHome,
    latest: Latest,
    watch: WatchSnapshot,
    listState: LazyListState,
    onPlay: (setId: String) -> Unit,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onToggleWatchlist: (setId: String, listed: Boolean) -> Unit,
    onSeeAll: (CatalogTab) -> Unit,
) {
    val editorial = magazine.editorial
    val watchlist = remember(watch) { watch.watchlist.toSet() }
    val width = LocalConfiguration.current.screenWidthDp.dp
    val gutter = gutterFor(width)
    val compact = width <= CompactBreakpoint
    val series = latest.series
    val courses = latest.courses

    // `columnWidth` (this `BoxWithConstraints`'s own measured width) is
    // deliberately not `width` (the window's own `screenWidthDp`): every
    // fluid size below is `vw`-relative on the web, which a rail beside
    // this column never narrows, but `HomeShelfRow`'s own poster formula is
    // a CSS `%` of its *own* grid container (`catalog.css:35`) — the
    // rendered column, narrower than the window by whatever `LibraryRail`
    // takes on EXPANDED. Feeding it `width` instead once sized posters for
    // the window's own width, too wide by the rail's whole share of it.
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columnWidth = maxWidth
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (compact) 64.dp else 96.dp),
        ) {
            if (editorial.cover.isNotEmpty()) {
                item(key = "cover") {
                    HomeCover(
                        films = editorial.cover, watchlist = watchlist, width = width,
                        onPlay = { onPlay(it.setId) }, onOpenTitle = onOpenTitle, onToggleWatchlist = onToggleWatchlist,
                    )
                }
            }
            if (editorial.features.isNotEmpty()) {
                item(key = "features") {
                    Box(Modifier.padding(top = 12.dp)) { HomeFeatures(features = editorial.features, width = width, onOpenTitle = onOpenTitle) }
                }
            }
            if (magazine.resumeCards.isNotEmpty() || editorial.quote != null) {
                item(key = "continue") {
                    Box(Modifier.padding(top = 28.dp)) {
                        ContinueBand(
                            cards = magazine.resumeCards, quote = editorial.quote, width = width,
                            onPlay = onPlay, onOpenTitle = onOpenTitle, onSeeAllContinue = { onSeeAll(CatalogTab.Kept(KeptKind.CONTINUE)) },
                        )
                    }
                }
            }
            if (magazine.recentlyAdded.isNotEmpty() || editorial.thisMonth.isNotEmpty()) {
                item(key = "recent") {
                    Box(Modifier.padding(top = 28.dp)) {
                        RecentBand(
                            recentlyAdded = magazine.recentlyAdded, totalFilms = magazine.recentlyAddedTotal, thisMonth = editorial.thisMonth,
                            width = width, onOpenTitle = onOpenTitle, onSeeAllMovies = { onSeeAll(CatalogTab.Dept(Department.MOVIES)) },
                        )
                    }
                }
            }
            if (series.isNotEmpty()) {
                item(key = "series") {
                    Column(modifier = Modifier.padding(top = 36.dp, start = gutter, end = gutter)) {
                        BandHeading(title = "Latest series", count = latest.seriesTotal, onSeeAll = { onSeeAll(CatalogTab.Dept(Department.SERIES)) })
                        HomeShelfRow(shows = series, width = columnWidth - gutter * 2, compact = compact, onOpen = onOpenCollection)
                    }
                }
            }
            if (courses.isNotEmpty()) {
                item(key = "courses") {
                    Column(modifier = Modifier.padding(top = 36.dp, start = gutter, end = gutter)) {
                        BandHeading(title = "Latest courses", count = latest.coursesTotal, onSeeAll = { onSeeAll(CatalogTab.Dept(Department.TUTORIALS)) })
                        CourseList(courses = courses, onOpen = onOpenCollection)
                    }
                }
            }
        }
    }
}
