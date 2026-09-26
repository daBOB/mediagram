package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import catalog.CollectionKind
import catalog.Division
import catalog.Entry
import catalog.ResumeVerb
import catalog.SeriesFacts
import catalog.Shelf
import catalog.detailRows
import catalog.episodeShort
import catalog.extentOf
import catalog.firstItemOf
import catalog.pictureLine
import catalog.provenance
import catalog.rowsOf
import catalog.scaleLine
import catalog.seriesResumeFor
import catalog.showsOf
import catalog.similarShows
import catalog.summarize
import catalog.walk
import catalog.yearLine
import designsystem.Spacing
import model.TitleCredits
import model.WatchSnapshot
import model.ageLabel
import uniffi.mediagram_core.TitleInfo

/**
 * What is inside one show or course: a show's own feature-article page
 * ([SeriesPage]) when [collection] is one, else what a course has always
 * been — its chapters and lessons, flattened once and shown as one
 * indented list, the same list a season screen shows for the one division
 * a search result may still open on its own.
 *
 * Every parameter beyond the original eight defaults to something inert, so
 * a caller not yet wired for credits, similar shows or a person page keeps
 * compiling — see [TitleDetailScreen]'s own doc comment for the same rule
 * on the film side.
 */
@Composable
fun CollectionScreen(
    collection: Entry.Collection,
    info: TitleInfo?,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    posterPath: suspend (key: String) -> String?,
    onOpenTitle: (setId: String) -> Unit,
    onOpenSeason: (Division) -> Unit,
    onOpenGenre: (String) -> Unit,
    shelves: List<Shelf> = emptyList(),
    onOpenCollection: (key: String) -> Unit = {},
    onOpenPerson: (Long) -> Unit = {},
    onPlay: (setId: String) -> Unit = {},
    editorsChoice: String? = null,
    onToggleEditorsChoice: (() -> Unit)? = null,
    onToggleWatchlist: () -> Unit = {},
    titleCredits: suspend (String) -> TitleCredits = { TitleCredits.Empty },
    fetchPortrait: suspend (Long) -> String? = { null },
    shouldRequestPortrait: (Long) -> Boolean = { false },
    season: String? = null,
    onSelectSeason: (String) -> Unit = {},
) {
    if (collection.kind == CollectionKind.SHOW) {
        SeriesPage(
            collection, info, watch, heldIds, onOpenTitle, onOpenGenre, shelves, onOpenCollection,
            onOpenPerson, onPlay, editorsChoice, onToggleEditorsChoice, onToggleWatchlist,
            titleCredits, fetchPortrait, shouldRequestPortrait, season, onSelectSeason,
        )
        return
    }

    // Flattened once per collection, not on every recomposition: the depth
    // becomes an indent here because a lazy list cannot nest, and a viewer
    // still has to see which folder holds what.
    val rows = remember(collection) { rowsOf(collection.divisions) }
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        item {
            Text(
                text = collection.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = Spacing.medium),
            )
        }
        // A course is rated as a course, so any lesson speaks for it — the
        // first, as `series-header.js` asks. A course has no rating of its own.
        val firstEpisode = firstItemOf(collection.divisions)
        val age = firstEpisode?.ageLabel()
        val genres = firstEpisode?.genres ?: emptyList()
        if (info != null || collection.posterPath != null || age != null || genres.isNotEmpty()) {
            item(key = "header") {
                TitleHeader(
                    posterPath = collection.posterPath,
                    title = collection.name,
                    facts = age,
                    info = info,
                    genres = genres,
                    onOpenGenre = onOpenGenre,
                    modifier = Modifier.padding(bottom = Spacing.medium),
                )
            }
        }
        items(rows, positions, watchedIds, heldIds, onOpenTitle)
    }
}

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
 * same season — [ui.LibraryPositions.setCollectionSeason] is the caller
 * every real screen wires this to.
 */
@Composable
private fun SeriesPage(
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
    shouldRequestPortrait: (Long) -> Boolean,
    season: String?,
    onSelectSeason: (String) -> Unit,
) {
    val firstEpisode = remember(collection) { firstItemOf(collection.divisions) }
    val resume = remember(collection, watch) { seriesResumeFor(collection, watch) }
    val credits = rememberTitleCredits(collection.posterKey, titleCredits)
    val facts = remember(collection) { summarize(collection.divisions) }

    val defaultSeason =
        remember(collection) {
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

    // A LazyColumn rather than a scrolling Column, since Episodes can run to
    // a few hundred rows: those join this list directly ([seriesEpisodes])
    // instead of nesting a second lazily-scrolled list inside this one,
    // which either crashes on unbounded height or clips to whichever bound
    // wins. The one visible cost is the spread's own backdrop, inset by the
    // same margin as everything else here rather than filling the edge —
    // a deliberate, minor difference from the film page's edge-to-edge hero.
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Spacing.large, vertical = Spacing.medium),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        item(key = "spread") {
            TitleSpread(
                backdropPath = firstEpisode?.backdropPath ?: firstEpisode?.posterPath ?: collection.posterPath,
                title = collection.name,
                facts = seriesFactsLine(facts, firstEpisode?.genres.orEmpty()),
                overview = info?.overview,
                tagline = info?.tagline,
            )
        }
        if (firstEpisode != null) {
            item(key = "pills") {
                TitlePills(
                    playLabel = resume?.let { "${verbLabel(it.verb)} ${episodeShort(it.set)}" },
                    onPlay = { resume?.let { onPlay(it.set.setId) } },
                    watchlisted = firstEpisode.setId in watch.watchlist,
                    onToggleWatchlist = onToggleWatchlist,
                    editorsChoicePinned = editorsChoice == firstEpisode.setId,
                    onToggleEditorsChoice = onToggleEditorsChoice,
                )
            }
        }
        item(key = "tab-row") { TitleTabRow(labels, shownTab, onSelectTab, modifier = Modifier.padding(top = Spacing.small)) }
        when (shownTab) {
            "Episodes" -> seriesEpisodes(collection, shownSeason, onSelectSeason, watch, heldIds, onOpenTitle)
            "Cast" ->
                item(key = "cast") {
                    CastPanel(credits, onOpenPerson, fetchPortrait, shouldRequestPortrait)
                }
            "Similar" ->
                item(key = "similar") {
                    SeriesSimilarTab(collection, watch, shelves, onOpenCollection)
                }
            else ->
                item(key = "about") {
                    FactSheet(seriesAboutRows(facts, info, firstEpisode?.genres.orEmpty(), onOpenGenre))
                }
        }
    }
}

/** `1987–1994 · 2 seasons · Sci-Fi, Drama` — a Compose port of `series-page.js`'s own `factsLine`. */
private fun seriesFactsLine(
    facts: SeriesFacts,
    genres: List<String>,
): String? =
    listOfNotNull(yearLine(facts), "${facts.seasons} ${if (facts.seasons == 1) "season" else "seasons"}", genres.take(3).joinToString(", ").takeIf(String::isNotEmpty))
        .joinToString(" · ")
        .takeIf(String::isNotEmpty)

private fun seriesAboutRows(
    facts: SeriesFacts,
    info: TitleInfo?,
    genres: List<String>,
    onOpenGenre: (String) -> Unit,
): List<Pair<String, (@Composable () -> Unit)?>> =
    buildList {
        add("Aired" to textFact(yearLine(facts)))
        add("Held" to textFact(scaleLine(facts)))
        add("From" to textFact(provenance(info)))
        add("Genres" to genreFact(genres, onOpenGenre))
        add("Picture" to textFact(pictureLine(facts)))
        for ((label, value) in detailRows(facts)) add(label to textFact(value))
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

private fun verbLabel(verb: ResumeVerb): String =
    when (verb) {
        ResumeVerb.RESUME -> "Resume"
        ResumeVerb.CONTINUE -> "Continue"
        ResumeVerb.PLAY -> "Play"
    }
