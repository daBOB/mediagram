package ui.tv.catalog

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import catalog.Entry
import catalog.GenreIndexEntry
import catalog.HomeRow
import catalog.MoviesDepartment
import catalog.RowContent
import catalog.Shelf
import catalog.ShowsDepartment
import catalog.factsLine
import catalog.firstItemOf
import catalog.moviesDepartmentOf
import catalog.showsDepartmentOf
import catalog.walk
import designsystem.Overscan
import designsystem.Spacing
import java.io.File
import model.Kind
import model.MediaSet
import model.WatchSnapshot
import ui.tv.TvTextRow

/** How wide a tile is on a department page's film and genre rows. */
private val DeptTileWidth = 160.dp

/**
 * The Movies department's own front page — the television twin of the
 * phone's department screen and the web's `department-pages.js#renderMoviesDept`:
 * a hero for the most popular unwatched film with a backdrop, then Featured,
 * Genres, Acclaimed and Recently added, and a link down to every film the
 * shelf holds. `null` [dept] (an empty Movies shelf) draws nothing — the
 * caller falls back to the plain shelf wall, which says so.
 *
 * Arrival focus lands on the first plate of the first non-empty row
 * (Featured, then Genres, then Acclaimed, then Recently added, then the "All
 * N films" link itself, in that order) — or on [restoreKey]'s own plate,
 * when it names one still on the page, the same "named stop, or the first"
 * rule every other page on this surface follows.
 */
@Composable
internal fun TvMoviesDepartmentPage(
    dept: MoviesDepartment,
    onOpenTitle: (setId: String) -> Unit,
    onOpenGenre: (name: String) -> Unit,
    onOpenAllFilms: () -> Unit,
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
) {
    val first = remember { FocusRequester() }
    val target =
        remember(dept, restoreKey) {
            restoreKey?.let { wanted ->
                dept.featured.indexOfFirst { it.setId == wanted }.takeIf { it >= 0 }?.let { "featured" to it }
                    ?: dept.acclaimed.indexOfFirst { it.setId == wanted }.takeIf { it >= 0 }?.let { "acclaimed" to it }
                    ?: dept.recentlyAdded.indexOfFirst { it.setId == wanted }.takeIf { it >= 0 }?.let { "recentlyAdded" to it }
            } ?: when {
                dept.featured.isNotEmpty() -> "featured" to 0
                dept.genres.isNotEmpty() -> "genres" to 0
                dept.acclaimed.isNotEmpty() -> "acclaimed" to 0
                dept.recentlyAdded.isNotEmpty() -> "recentlyAdded" to 0
                else -> "all" to 0
            }
        }
    LaunchedEffect(dept, restoreKey) { first.requestFocus() }

    TvPage {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = Overscan.vertical),
        ) {
            dept.lead?.let { lead -> TvCoverStory(films = listOf(lead), onPlay = onOpenTitle, onOpenTitle = onOpenTitle) }
            Column(modifier = Modifier.padding(horizontal = Overscan.horizontal)) {
                DeptRow(
                    "Featured",
                    dept.featured,
                    onOpenTitle,
                    focusAt = target.second.takeIf { target.first == "featured" },
                    focus = first,
                    heldIds = heldIds,
                )
                if (dept.genres.isNotEmpty()) {
                    TvSectionHeading("Genres", modifier = Modifier.padding(top = Spacing.large))
                    GenreTileRow(dept.genres, onOpenGenre, focusAt = target.second.takeIf { target.first == "genres" }, focus = first)
                }
                DeptRow(
                    "Acclaimed, not yet seen",
                    dept.acclaimed,
                    onOpenTitle,
                    focusAt = target.second.takeIf { target.first == "acclaimed" },
                    focus = first,
                    heldIds = heldIds,
                )
                DeptRow(
                    "Recently added",
                    dept.recentlyAdded,
                    onOpenTitle,
                    focusAt = target.second.takeIf { target.first == "recentlyAdded" },
                    focus = first,
                    heldIds = heldIds,
                )
                TvTextRow(
                    text = "All ${dept.filmCount} films",
                    onClick = onOpenAllFilms,
                    focusRequester = first.takeIf { target.first == "all" },
                    modifier = Modifier.padding(top = Spacing.large, bottom = Spacing.medium),
                )
            }
        }
    }
}

/**
 * Every film the Movies shelf holds, one flat wall — "All N films" from the
 * department's own front page, and the phase-3 `MOVIES_PAGE` frame's own
 * screen: one lazy-scrolling wall rather than the web's numbered pages,
 * since Android renders the whole of it at once (see `LibraryPositions`'s
 * own doc on `MOVIES_PAGE`).
 */
@Composable
internal fun TvMoviesPage(
    films: List<MediaSet>,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
) {
    val (positions, watchedIds) = rememberWatchMarks(watch)
    TvPage {
        TvWall(
            items = films,
            key = MediaSet::setId,
            restoreKey = restoreKey,
            onOpen = { set -> onOpenTitle(set.setId) },
            header = { TvCountedHeading("All films", films.size) },
            plate = { set, modifier, onOpen ->
                TvEntryPlate(Entry.Film(set), positions, watchedIds, onOpen, modifier, heldIds)
            },
        )
    }
}

/**
 * The Series or Tutorials department's own front page — `renderShowsDept`'s
 * television twin: a hero for the most popular show with a backdrop, then
 * what is underway, Popular and New episodes (both empty below a dozen
 * shows — [ShowsDepartment]'s own gate), and finally every show the shelf
 * holds, as one wall rather than a separate link: unlike Movies, a show's
 * own card is already the whole of what "all" would add, so there is no
 * second flat page to send "All N" to.
 */
@Composable
internal fun TvShowsDepartmentPage(
    dept: ShowsDepartment,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
) {
    val (positions, watchedIds) = rememberWatchMarks(watch)
    val underwayEntries =
        remember(dept) { (dept.underway.continues.map(Entry::Film) + dept.underway.nextUp.map { Entry.Film(it.set) }) }
    TvPage {
        TvWall(
            items = dept.all,
            key = Entry.Collection::key,
            restoreKey = restoreKey,
            onOpen = { entry -> onOpenCollection(entry.key) },
            header = {
                Column {
                    dept.lead?.let { lead ->
                        val cover = leadCover(lead)
                        if (cover != null) TvCoverStory(films = listOf(cover), onPlay = { onOpenCollection(lead.key) }, onOpenTitle = { onOpenCollection(lead.key) })
                    }
                    Column(modifier = Modifier.padding(bottom = Spacing.medium)) {
                        if (underwayEntries.isNotEmpty()) {
                            TvHomeRow(
                                row = HomeRow("Continue", seeAll = null, total = underwayEntries.size, content = RowContent.Entries(underwayEntries)),
                                positions = positions,
                                watchedIds = watchedIds,
                                onOpenTitle = onOpenTitle,
                                onOpenCollection = onOpenCollection,
                                onSeeAll = {},
                                focusAt = null,
                                focus = Modifier,
                            )
                        }
                        if (dept.popular.isNotEmpty()) {
                            TvHomeRow(
                                row = HomeRow("Popular", seeAll = null, total = dept.popular.size, content = RowContent.Entries(dept.popular)),
                                positions = positions,
                                watchedIds = watchedIds,
                                onOpenTitle = onOpenTitle,
                                onOpenCollection = onOpenCollection,
                                onSeeAll = {},
                                focusAt = null,
                                focus = Modifier,
                            )
                        }
                        if (dept.newEpisodes.isNotEmpty()) {
                            TvHomeRow(
                                row = HomeRow("New episodes", seeAll = null, total = dept.newEpisodes.size, content = RowContent.Entries(dept.newEpisodes)),
                                positions = positions,
                                watchedIds = watchedIds,
                                onOpenTitle = onOpenTitle,
                                onOpenCollection = onOpenCollection,
                                onSeeAll = {},
                                focusAt = null,
                                focus = Modifier,
                            )
                        }
                    }
                    TvCountedHeading("Every show", dept.all.size)
                }
            },
            plate = { entry, modifier, onOpen -> TvEntryPlate(entry, positions, watchedIds, onOpen, modifier, heldIds) },
        )
    }
}

/** A show's own first episode, standing in for it on a hero built for [MediaSet] — the same swap `SeriesPageState.leadOf` makes. */
private fun leadCover(entry: Entry.Collection): MediaSet? = firstItemOf(entry.divisions)?.copy(setId = entry.key)

/**
 * A shelf as its own department front page, when it has one built —
 * Movies gets [TvMoviesDepartmentPage], Series and Tutorials get
 * [TvShowsDepartmentPage] (told apart by [Shelf.title] the way the masthead
 * itself names them). Every department view model answers `null` for an
 * empty shelf, and this falls back to the plain wall then, the same "nothing
 * to show" this surface already draws for every other empty state — a
 * department is a richer *front page*, never a replacement for the shelf
 * itself.
 *
 * `indexById` (`feature:catalog`'s own name for this) is `internal` to that
 * module and unreachable from here, so [byIdOf] rebuilds the same map, but
 * scoped to just [shows] rather than the whole library: all
 * `showsDepartmentOf` asks its own `byId` for is resolving a *these shows'*
 * progress row to the set it belongs to.
 *
 * No size floor of its own here — the web's `renderMoviesDept`/`renderShowsDept`
 * always draw the department page for a non-empty shelf; Movies' own rows
 * (Featured, Genres, Acclaimed, Recently added) show whenever the view model
 * answers something for them, and `showsDepartmentOf` is where Popular/New
 * episodes already gate themselves past a dozen shows — this composable adds
 * no gate of its own on top of that.
 */
@Composable
internal fun DepartmentOrShelfWall(
    shelf: Shelf,
    watch: WatchSnapshot,
    heldIds: Set<String>,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onOpenGenre: (name: String) -> Unit,
    onOpenMoviesPage: () -> Unit,
    restoreKey: String?,
) {
    if (shelf.title == "Movies") {
        val films = remember(shelf) { shelf.entries.filterIsInstance<Entry.Film>().map { it.set } }
        val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
        val dept = remember(films, watchedIds) { moviesDepartmentOf(films) { it in watchedIds } }
        if (dept != null) {
            TvMoviesDepartmentPage(dept, onOpenTitle, onOpenGenre, onOpenMoviesPage, restoreKey, heldIds)
            return
        }
    } else {
        val shows = remember(shelf) { shelf.entries.filterIsInstance<Entry.Collection>() }
        val kind = if (shelf.title == "Tutorials") Kind.TUTORIAL else Kind.EPISODE
        val dept = remember(shows, watch) { showsDepartmentOf(kind, shows, byIdOf(shows), watch) }
        if (dept != null) {
            TvShowsDepartmentPage(dept, watch, onOpenTitle, onOpenCollection, restoreKey, heldIds)
            return
        }
    }
    TvShelfWall(shelf, watch, onOpenTitle, onOpenCollection, restoreKey, heldIds)
}

private fun byIdOf(shows: List<Entry.Collection>): Map<String, MediaSet> {
    val byId = HashMap<String, MediaSet>()
    for (show in shows) {
        for (division in show.divisions.asSequence().flatMap { it.walk() }) {
            for (set in division.items) byId[set.setId] = set
        }
    }
    return byId
}

@Composable
private fun DeptRow(
    title: String,
    films: List<MediaSet>,
    onOpenTitle: (setId: String) -> Unit,
    focusAt: Int? = null,
    focus: FocusRequester? = null,
    heldIds: Set<String> = emptySet(),
) {
    if (films.isEmpty()) return
    TvSectionHeading(title, modifier = Modifier.padding(top = Spacing.large))
    Column(modifier = Modifier.padding(top = Spacing.small)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        ) {
            films.forEachIndexed { index, set ->
                TvPlate(
                    title = set.title,
                    posterPath = set.posterPath?.let(::File),
                    onOpen = { onOpenTitle(set.setId) },
                    modifier =
                        Modifier
                            .width(DeptTileWidth)
                            .let { if (index == focusAt && focus != null) it.focusRequester(focus) else it },
                    caption = factsLine(set.year, set.durationSecs),
                    held = set.setId in heldIds,
                )
            }
        }
    }
}

@Composable
private fun GenreTileRow(
    genres: List<GenreIndexEntry>,
    onOpenGenre: (name: String) -> Unit,
    focusAt: Int? = null,
    focus: FocusRequester? = null,
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(top = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        genres.forEachIndexed { index, genre ->
            TvPlate(
                title = genre.name,
                posterPath = genre.art?.let(::File),
                onOpen = { onOpenGenre(genre.name) },
                modifier =
                    Modifier
                        .width(DeptTileWidth)
                        .let { if (index == focusAt && focus != null) it.focusRequester(focus) else it },
                caption = titleCountLabel(genre.count),
            )
        }
    }
}

/** `1 title` / `12 titles` — the same count line `TvLists`' own rows use for a list. */
private fun titleCountLabel(count: Int): String = "$count ${if (count == 1) "title" else "titles"}"
