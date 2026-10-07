package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalDensity
import catalog.CollectionRow
import catalog.Entry
import catalog.SeriesResumePick
import catalog.firstItemOf
import catalog.rowsOf
import catalog.seriesAboutFacts
import catalog.seriesFactsLine
import catalog.summarize
import catalog.walk
import data.PortraitRequestLog
import designsystem.Overscan
import designsystem.Spacing
import model.TitleCredits
import model.WatchSnapshot
import uniffi.mediagram_core.TitleInfo

/**
 * A show's own page on television — `series-page.js`: the opening spread
 * with the pills that start it, then Episodes (a season picker over that
 * season's episodes), About, Cast (once credits name somebody), Similar.
 *
 * The resume pill takes the remote on arrival, as Play does on a film's
 * page; [restoreKey] instead lands it where the viewer left from — an
 * episode that played, a genre in About, a person in Cast, a show in
 * Similar — on that tab, recomputed fresh each time for [TvTitlePage]'s
 * reason. The season shown is [season], the one last picked, else the one
 * holding the episode coming back to, else the resume point's, else the
 * first — the web's "the URL's, else the resume point's, else the first",
 * with the episode's own season added so the way back from it always finds
 * it on screen.
 *
 * One lazy list for the whole page, as the phone's: a season's episodes
 * join it directly rather than nesting a second scrolling list inside it.
 */
@Composable
internal fun TvSeriesPage(
    collection: Entry.Collection,
    info: TitleInfo?,
    watch: WatchSnapshot,
    onPlay: (setId: String) -> Unit,
    onOpenGenre: (String) -> Unit,
    restoreKey: String?,
    heldIds: Set<String>,
    credits: TitleCredits,
    onOpenPerson: (personId: Long) -> Unit,
    portraits: PortraitRequestLog,
    fetchPortrait: suspend (Long) -> String?,
    similar: List<Entry.Collection>,
    onOpenCollection: (key: String) -> Unit,
    resume: SeriesResumePick?,
    onResume: (setId: String) -> Unit,
    season: String?,
    onSelectSeason: (String) -> Unit,
    onToggleWatchlist: () -> Unit,
    editorsChoice: String?,
    onToggleEditorsChoice: (() -> Unit)?,
) {
    val first = remember(collection) { firstItemOf(collection.divisions) }
    val facts = remember(collection) { summarize(collection.divisions) }
    val genres = first?.genres.orEmpty()
    val marks = rememberWatchMarks(watch)
    val shown =
        remember(collection, season, restoreKey, resume) {
            fun holding(setId: String?) = collection.divisions.find { d -> d.walk().any { div -> div.items.any { it.setId == setId } } }
            collection.divisions.find { it.title == season } ?: holding(restoreKey) ?: holding(resume?.set?.setId) ?: collection.divisions.firstOrNull()
        }
    // The season's own heading is left out: the picker above already names it, as the web's list does.
    val rows = remember(shown) { shown?.let { rowsOf(listOf(it)).drop(1) }.orEmpty() }
    val tabs =
        remember(credits) {
            buildList {
                add("Episodes")
                add("About")
                if (credits.cast.isNotEmpty()) add("Cast")
                add("Similar")
            }
        }
    val initialTab =
        remember(tabs, credits, similar, restoreKey) {
            when {
                restoreKey == null -> 0
                restoreKey in genres -> tabs.indexOf("About")
                credits.onCastTab(restoreKey) -> tabs.indexOf("Cast")
                similar.any { it.key == restoreKey } -> tabs.indexOf("Similar")
                else -> 0
            }
        }
    var selected by rememberSaveable(collection.key) { mutableIntStateOf(initialTab) }
    LaunchedEffect(initialTab) { if (restoreKey != null && initialTab > 0) selected = initialTab }
    if (selected >= tabs.size) selected = 0

    val list = rememberLazyListState()
    val play = remember { FocusRequester() }
    val listPill = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    val picker = collection.divisions.size > 1
    val restoredRow = remember(rows, restoreKey) { rows.indexOfFirst { it is CollectionRow.Item && it.set.setId == restoreKey }.takeIf { it >= 0 } }
    val inset = with(LocalDensity.current) { Overscan.vertical.roundToPx() }

    TvPage {
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = Overscan.vertical),
            verticalArrangement = Arrangement.spacedBy(Spacing.small),
        ) {
            item(key = "spread") {
                TvTitleSpread(
                    backdropPath = first?.backdropPath ?: first?.posterPath ?: collection.posterPath,
                    title = collection.name,
                    facts = seriesFactsLine(facts, info, genres),
                    overview = info?.overview,
                    tagline = info?.tagline,
                ) {
                    TvSeriesPills(first, resume, onResume, watch, onToggleWatchlist, editorsChoice, onToggleEditorsChoice, play, listPill)
                }
            }
            item(key = "tabs") {
                TvSectionTabs(
                    titles = tabs,
                    selected = selected,
                    onSelect = { selected = it },
                    modifier = Modifier.padding(horizontal = Overscan.horizontal).revealsPageBelow { list.layoutInfo.viewportSize.height },
                )
            }
            val panel = Modifier.padding(horizontal = Overscan.horizontal).padding(top = Spacing.small)
            when (tabs[selected]) {
                "About" -> item(key = "about") { TvFactSheet(seriesAboutFacts(facts, info, genres), panel, onOpenGenre = onOpenGenre, restoreKey = restoreKey) }
                "Cast" -> item(key = "cast") { Box(panel) { TvCastRow(credits, onOpenPerson, portraits, fetchPortrait, restoreKey) } }
                "Similar" -> item(key = "similar") { Box(panel) { TvSimilarShows(similar, onOpenCollection, restoreKey) } }
                else -> {
                    if (picker) item(key = "season-picker") { TvSeasonPicker(collection.divisions, shown, onSelectSeason, panel) }
                    itemsIndexed(rows, key = { index, row -> rowKeyOf(row, index) }) { index, row ->
                        TvCollectionRow(row, marks, heldIds, onPlay, rowFocus.takeIf { index == restoredRow }, Modifier.padding(horizontal = Overscan.horizontal))
                    }
                }
            }
        }
    }
    // Arrival: back to the episode that played, else the pill that starts the show — never on a
    // tab press, which leaves the remote on its tab.
    LaunchedEffect(collection.key) {
        val row = restoredRow
        when {
            selected != 0 -> Unit
            row != null -> {
                // Spread and tabs come first, then the picker when there is one.
                list.scrollToItem(2 + (if (picker) 1 else 0) + row, -inset)
                rowFocus.requestFocus()
            }
            restoreKey !in genres -> (if (resume != null) play else listPill).requestFocus()
        }
    }
}
