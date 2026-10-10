package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import catalog.Entry
import catalog.Shelf
import catalog.extentOf
import catalog.firstItemOf
import catalog.resumeWordsOf
import catalog.seriesAboutFacts
import catalog.seriesFactsLine
import catalog.seriesResumeFor
import catalog.showsOf
import catalog.similarShows
import catalog.summarize
import catalog.walk
import data.PortraitRequestLog
import designsystem.Spacing
import model.TitleCredits
import model.WatchSnapshot
import ui.common.catalog.rememberTitleCredits
import uniffi.mediagram_core.TitleInfo

/**
 * A show's own page, as a feature article with a table of contents: the
 * opening spread, the pills that start it, then tabs — Episodes (a season
 * picker over a readable list), About, Cast (once credits name somebody),
 * Similar. A Compose port of `series-page.js`.
 *
 * [season]/[onSelectSeason] are the caller's own state, not this
 * composable's — a `null` [season] means nobody has chosen one yet, so this
 * page picks its own first default (a resume point's own season, else the
 * first) and asks the caller to remember whichever one is actually shown
 * ([shownSeason] below) once the picker changes it. Kept outside so a title
 * opened from Similar, Cast or an episode and left again still finds the
 * same season — [ui.common.LibraryPositions.setCollectionSeason] is the caller
 * every real screen wires this to.
 */
@Composable
internal fun SeriesPage(
    collection: Entry.Collection,
    info: TitleInfo?,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    onOpenTitle: (setId: String) -> Unit,
    onOpenGenre: (String) -> Unit,
    shelves: List<Shelf>,
    onOpenCollection: (key: String) -> Unit,
    onOpenPerson: (Long) -> Unit,
    onPlay: (setId: String) -> Unit,
    editorsChoice: String?,
    onToggleEditorsChoice: (() -> Unit)?,
    onToggleWatchlist: () -> Unit,
    titleCredits: suspend (String) -> TitleCredits,
    fetchPortrait: suspend (Long) -> String?,
    portraits: PortraitRequestLog,
    season: String?,
    onSelectSeason: (String) -> Unit,
    state: LazyListState,
) {
    val firstEpisode = remember(collection) { firstItemOf(collection.divisions) }
    val resume = remember(collection, watch) { seriesResumeFor(collection, watch) }
    val credits = rememberTitleCredits(collection.posterKey, titleCredits)
    val facts = remember(collection) { summarize(collection.divisions) }

    // Keyed on [resume] too: watch state usually arrives after the first
    // frame, and a default picked once, before it, never opened on the
    // season the resume point is in.
    val defaultSeason =
        remember(collection, resume) {
            collection.divisions.find { d -> resume != null && d.walk().any { div -> div.items.any { it.setId == resume.set.setId } } }
                ?.title ?: collection.divisions.firstOrNull()?.title
        }
    val shownSeason = season ?: defaultSeason

    val labels =
        buildList {
            add("Episodes")
            add("About")
            if (credits.cast.isNotEmpty()) add("Cast")
            add("Similar")
        }
    val (shownTab, onSelectTab) = rememberChosenTab(labels)
    // The spread is a lazy item here, so its art-in is held by the page above
    // the list: a scroll away and back finds it played, a new visit plays it.
    val reveals = rememberReveals()

    // A LazyColumn rather than a scrolling Column, since Episodes can run to
    // a few hundred rows: those join this list directly ([seriesEpisodes])
    // instead of nesting a second lazily-scrolled list inside this one,
    // which either crashes on unbounded height or clips to whichever bound
    // wins. The one visible cost, on a phone, is the spread's own backdrop,
    // inset by the same margin as everything else here rather than filling
    // the edge — a deliberate, minor difference from the film page's
    // edge-to-edge hero. A wide window's spread reaches back over the margin
    // ([TitleSpread]'s `bleed`), since there the art is the page's opener.
    LazyColumn(
        state = state,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.large, vertical = Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        item(key = "spread") {
            TitleSpread(
                backdropPath = firstEpisode?.backdropPath ?: firstEpisode?.posterPath ?: collection.posterPath,
                title = collection.name,
                facts = seriesFactsLine(facts, info, firstEpisode?.genres.orEmpty()),
                overview = info?.overview,
                tagline = info?.tagline,
                bleed = Spacing.large,
                reveals = reveals,
            ) {
                if (firstEpisode != null) {
                    TitlePills(
                        playLabel = resume?.let(::resumeWordsOf),
                        onPlay = { resume?.let { onPlay(it.set.setId) } },
                        watchlisted = firstEpisode.setId in watch.watchlist,
                        onToggleWatchlist = onToggleWatchlist,
                        editorsChoicePinned = editorsChoice == firstEpisode.setId,
                        onToggleEditorsChoice = onToggleEditorsChoice,
                    )
                }
            }
        }
        item(key = "tab-row") { TitleTabRow(labels, shownTab, onSelectTab, modifier = Modifier.padding(top = Spacing.small)) }
        when (shownTab) {
            "Episodes" -> seriesEpisodes(collection, shownSeason, onSelectSeason, watch, heldIds, onPlay)
            "Cast" ->
                item(key = "cast") {
                    CastPanel(credits, onOpenPerson, fetchPortrait, portraits)
                }
            "Similar" ->
                item(key = "similar") {
                    SeriesSimilarTab(collection, watch, shelves, onOpenCollection)
                }
            else ->
                item(key = "about") {
                    FactSheet(factRows(seriesAboutFacts(facts, info, firstEpisode?.genres.orEmpty()), onOpenGenre))
                }
        }
    }
}

@Composable
private fun SeriesSimilarTab(
    collection: Entry.Collection,
    watch: WatchSnapshot,
    shelves: List<Shelf>,
    onOpenCollection: (key: String) -> Unit,
) {
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    val similar =
        remember(collection.key, shelves, watchedIds) {
            similarShows(collection, showsOf(shelves), watched = { it in watchedIds })
        }
    PosterRow(
        similar.map { show ->
            PosterRowItem(
                key = show.key,
                posterPath = show.posterPath,
                title = show.name,
                caption = extentOf(show),
                onClick = { onOpenCollection(show.key) },
            )
        },
    )
}
