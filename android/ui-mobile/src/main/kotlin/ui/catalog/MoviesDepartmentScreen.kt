package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import catalog.pickFeatured
import designsystem.Spacing
import model.MediaSet
import model.WatchSnapshot
import uniffi.mediagram_core.TitleInfo
import kotlin.random.Random

/** How wide one poster runs in a department's own horizontal rows. */
private val DEPT_CARD_WIDTH = 140.dp

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
) {
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    var reel by remember { mutableStateOf<List<MediaSet>?>(null) }
    val featured = remember(films, watchedIds) { pickFeatured(films, watchedIds, Random, 1) }

    LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Spacing.large)) {
        item {
            DepartmentHero(
                kicker = "Only in your library",
                title = "Movies",
                line = movieDeptLine(department),
                lead = department.lead,
                onOpenTitle = onOpenTitle,
            )
        }
        department.lead?.takeIf { !it.tagline.isNullOrEmpty() }?.let { lead ->
            item { PullQuote(set = lead, onOpenTitle = onOpenTitle) }
        }
        if (department.featured.isNotEmpty()) {
            item {
                DeptRowHeading(
                    title = "Featured Movies",
                    onSeeAll = onSeeAllFilms,
                    seeAllLabel = "All ${department.filmCount} films →",
                    extra = { if (featured.isNotEmpty()) TextButton(onClick = { reel = pickFeatured(films, watchedIds, Random) }) { Text("Featured") } },
                )
            }
            item { FilmRow(department.featured, onOpenTitle) }
        }
        if (department.genres.isNotEmpty()) {
            item { DeptRowHeading(title = "Genres", onSeeAll = onOpenGenresIndex, seeAllLabel = "Every genre") }
            item { GenreRow(department.genres, onOpenGenre) }
        }
        if (department.acclaimed.isNotEmpty()) {
            item { DeptRowHeading(title = "Acclaimed, not yet seen") }
            item { FilmRow(department.acclaimed, onOpenTitle) }
        }
        if (department.recentlyAdded.isNotEmpty()) {
            item { DeptRowHeading(title = "Recently added", onSeeAll = onOpenLatest, seeAllLabel = "Latest") }
            item { FilmRow(department.recentlyAdded, onOpenTitle) }
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

/** "N films · H hours" — the web's own `line` for the Movies hero, dropping the hours when there are none. */
private fun movieDeptLine(department: MoviesDepartment): String =
    listOfNotNull(
        countOf(department.filmCount, "film"),
        department.hours.takeIf { it > 0 }?.let { "$it hours" },
    ).joinToString(" · ")

@Composable
private fun FilmRow(films: List<MediaSet>, onOpenTitle: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        contentPadding = PaddingValues(horizontal = Spacing.medium),
    ) {
        items(items = films, key = MediaSet::setId) { set ->
            PosterCard(
                posterPath = set.posterPath,
                title = set.title,
                caption = factsLine(set.year, set.durationSecs),
                watched = false,
                modifier = Modifier.width(DEPT_CARD_WIDTH),
                onClick = { onOpenTitle(set.setId) },
            )
        }
    }
}

@Composable
private fun GenreRow(genres: List<GenreIndexEntry>, onOpenGenre: (String) -> Unit) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        contentPadding = PaddingValues(horizontal = Spacing.medium),
    ) {
        items(items = genres, key = GenreIndexEntry::name) { entry ->
            PosterCard(
                posterPath = entry.art,
                title = entry.name,
                caption = countOf(entry.count, "title"),
                modifier = Modifier.width(DEPT_CARD_WIDTH),
                onClick = { onOpenGenre(entry.name) },
            )
        }
    }
}

/** The Movies shelf, unwrapped — every department page's own [Shelf] to [List]<[MediaSet]> reader. */
internal fun filmsOf(shelf: Shelf): List<MediaSet> = shelf.entries.filterIsInstance<Entry.Film>().map { it.set }
