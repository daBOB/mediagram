package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import catalog.Entry
import catalog.GenreIndexEntry
import catalog.MoviesDepartment
import catalog.Shelf
import catalog.factsLine
import catalog.moviesLineOf
import catalog.pickFeatured
import catalog.spelledCountOf
import designsystem.Spacing
import model.MediaSet
import model.WatchSnapshot
import uniffi.mediagram_core.TitleInfo
import kotlin.random.Random

/** How wide one poster runs in a department's own horizontal rows. */
private val DEPT_CARD_WIDTH = 140.dp

/** `.dept-row .genre-tiles{grid-auto-columns:minmax(12rem,1fr)}`. */
private val GENRE_ROW_TILE_WIDTH = 192.dp

/**
 * The Movies department's opening page — a Compose port of
 * `department-pages.js#renderMoviesDept`: a hero, Featured Movies (with the
 * same Featured reel the plain shelf offers), Genres as tiles, Acclaimed not
 * yet seen, Recently added, and the way into the whole, paged shelf.
 */
@Composable
internal fun MoviesDepartmentScreen(
    department: MoviesDepartment,
    films: List<MediaSet>,
    watch: WatchSnapshot,
    onOpenTitle: (String) -> Unit,
    onOpenGenre: (String) -> Unit,
    onOpenGenresIndex: () -> Unit,
    onOpenLatest: () -> Unit,
    onSeeAllFilms: () -> Unit,
    onPlay: (String) -> Unit,
    titleInfo: suspend (String) -> TitleInfo?,
    state: LazyListState = rememberLazyListState(),
) {
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    var reel by remember { mutableStateOf<List<MediaSet>?>(null) }
    val featured = remember(films, watchedIds) { pickFeatured(films, watchedIds, Random, 1) }
    // Figures, not spelled — the web builds this label from the length itself.
    val allFilms = "All ${department.filmCount} films"

    LazyColumn(state = state, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.large)) {
        item {
            DepartmentHero(
                title = "Movies",
                line = moviesLineOf(department),
                lead = department.lead,
                onOpenTitle = onOpenTitle,
            )
        }
        if (department.featured.isNotEmpty()) {
            item {
                DeptRowHeading(
                    title = "Featured Movies",
                    onSeeAll = onSeeAllFilms,
                    seeAllLabel = allFilms,
                    extra = { if (featured.isNotEmpty()) TextButton(onClick = { reel = pickFeatured(films, watchedIds, Random) }) { Text("Featured") } },
                )
            }
            item { FilmRow(department.featured, watchedIds, onOpenTitle) }
        }
        if (department.genres.isNotEmpty()) {
            item { DeptRowHeading(title = "Genres", onSeeAll = onOpenGenresIndex, seeAllLabel = "Every genre") }
            item { GenreRow(department.genres, onOpenGenre) }
        }
        if (department.acclaimed.isNotEmpty()) {
            item { DeptRowHeading(title = "Acclaimed, not yet seen") }
            item { FilmRow(department.acclaimed, watchedIds, onOpenTitle) }
        }
        if (department.recentlyAdded.isNotEmpty()) {
            item { DeptRowHeading(title = "Recently added", onSeeAll = onOpenLatest, seeAllLabel = "Latest") }
            item { FilmRow(department.recentlyAdded, watchedIds, onOpenTitle) }
        }
        // The way into the whole shelf at the page's foot, the web's `.dept-all`.
        item {
            PagePill(
                text = "$allFilms →",
                onClick = onSeeAllFilms,
                large = true,
                modifier = Modifier.padding(start = Spacing.medium, top = 56.dp),
            )
        }
    }

    reel?.let { picks ->
        FeaturedReel(
            films = picks,
            titleInfo = titleInfo,
            onPlay = { onPlay(it.setId) },
            onDetails = { onOpenTitle(it.setId) },
            onClose = { reel = null },
        )
    }
}

@Composable
private fun FilmRow(films: List<MediaSet>, watchedIds: Set<String>, onOpenTitle: (String) -> Unit) {
    LazyRow(
        state = rememberRowState(films.map(MediaSet::setId)),
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        contentPadding = PaddingValues(horizontal = Spacing.medium),
    ) {
        items(items = films, key = MediaSet::setId) { set ->
            PosterCard(
                posterPath = set.posterPath,
                title = set.title,
                caption = factsLine(set.year, set.durationSecs),
                watched = set.setId in watchedIds,
                modifier = Modifier.width(DEPT_CARD_WIDTH),
                onClick = { onOpenTitle(set.setId) },
            )
        }
    }
}

/**
 * Genres as tiles in one sideways row, at the web's own `minmax(12rem, 1fr)`
 * floor. The web stretches a short row's tiles to fill a wide window; here
 * they keep the floor's width, which only shows on a tablet whose library
 * holds fewer genres than fit across it.
 */
@Composable
private fun GenreRow(genres: List<GenreIndexEntry>, onOpenGenre: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = Spacing.medium),
    ) {
        items(items = genres, key = GenreIndexEntry::name) { entry ->
            ArtTile(
                name = entry.name,
                meta = spelledCountOf(entry.count, "title"),
                art = entry.art,
                aspectRatio = GENRE_ROW_TILE_ASPECT,
                onClick = { onOpenGenre(entry.name) },
                modifier = Modifier.width(GENRE_ROW_TILE_WIDTH),
            )
        }
    }
}

/** The Movies shelf, unwrapped — every department page's own [Shelf] to [List]<[MediaSet]> reader. */
internal fun filmsOf(shelf: Shelf): List<MediaSet> = shelf.entries.filterIsInstance<Entry.Film>().map { it.set }
