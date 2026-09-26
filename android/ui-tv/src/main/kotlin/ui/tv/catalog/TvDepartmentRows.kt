package ui.tv.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import catalog.Entry
import catalog.GenreIndexEntry
import catalog.factsLine
import catalog.keyOf
import designsystem.Spacing
import java.io.File
import kotlinx.coroutines.flow.first
import model.MediaSet
import model.Progress

/** How wide a tile is on a department page's film, entry and genre rows. */
internal val DeptTileWidth = 160.dp

/** How far past a department row's own edge it keeps plates composed — [TvWall]'s own reasoning, at a single row's smaller scale. */
private val DeptCacheAhead = 320.dp
private val DeptCacheBehind = 320.dp

/**
 * A curated department row of films — Featured, Acclaimed, Recently added —
 * lazily, so a page with four of these plus a genre row never composes the
 * ~50 plates that would otherwise all build in the same frame on a weak box.
 * [focusAt]/[focus] name the one stop [TvMoviesDepartmentPage] wants the
 * remote scrolled to and given, when this is that row.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DeptRow(
    title: String,
    films: List<MediaSet>,
    onOpenTitle: (setId: String) -> Unit,
    focusAt: Int? = null,
    focus: FocusRequester? = null,
    takesFocus: Boolean = true,
    heldIds: Set<String> = emptySet(),
) {
    if (films.isEmpty()) return
    TvSectionHeading(title, modifier = Modifier.padding(top = Spacing.large))
    val state = rememberLazyListState(cacheWindow = remember { LazyLayoutCacheWindow(ahead = DeptCacheAhead, behind = DeptCacheBehind) })
    LaunchedEffect(focusAt, takesFocus) { scrollThenFocus(state, focusAt, focus, takesFocus) }
    LazyRow(state = state, modifier = Modifier.padding(top = Spacing.small), horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        itemsIndexed(films, key = { _, set -> set.setId }) { index, set ->
            TvPlate(
                title = set.title,
                posterPath = set.posterPath?.let(::File),
                onOpen = { onOpenTitle(set.setId) },
                modifier = Modifier.width(DeptTileWidth).let { if (index == focusAt && focus != null) it.focusRequester(focus) else it },
                caption = factsLine(set.year, set.durationSecs),
                held = set.setId in heldIds,
            )
        }
    }
}

/** [DeptRow]'s own twin for a header row of mixed film/show [Entry] — Continue, Popular, New episodes — over [TvEntryPlate] rather than a bare poster. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun DeptEntryRow(
    title: String,
    entries: List<Entry>,
    positions: Map<String, Progress>,
    watchedIds: Set<String>,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    heldIds: Set<String>,
    focusAt: Int? = null,
    focus: FocusRequester? = null,
    takesFocus: Boolean = true,
) {
    if (entries.isEmpty()) return
    TvSectionHeading(title, modifier = Modifier.padding(top = Spacing.large))
    val state = rememberLazyListState(cacheWindow = remember { LazyLayoutCacheWindow(ahead = DeptCacheAhead, behind = DeptCacheBehind) })
    LaunchedEffect(focusAt, takesFocus) { scrollThenFocus(state, focusAt, focus, takesFocus) }
    LazyRow(state = state, modifier = Modifier.padding(top = Spacing.small), horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        itemsIndexed(entries, key = { _, entry -> keyOf(entry) }) { index, entry ->
            TvEntryPlate(
                entry = entry,
                positions = positions,
                watchedIds = watchedIds,
                onOpen = { openEntry(entry, onOpenTitle, onOpenCollection) },
                modifier = Modifier.width(DeptTileWidth).let { if (index == focusAt && focus != null) it.focusRequester(focus) else it },
                heldIds = heldIds,
            )
        }
    }
}

/** The Movies department's Genres row — up to a dozen tiles, [DeptRow]'s own twin over a genre's cover rather than a film's. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun GenreTileRow(
    genres: List<GenreIndexEntry>,
    onOpenGenre: (name: String) -> Unit,
    focusAt: Int? = null,
    focus: FocusRequester? = null,
    takesFocus: Boolean = true,
) {
    val state = rememberLazyListState(cacheWindow = remember { LazyLayoutCacheWindow(ahead = DeptCacheAhead, behind = DeptCacheBehind) })
    LaunchedEffect(focusAt, takesFocus) { scrollThenFocus(state, focusAt, focus, takesFocus) }
    LazyRow(state = state, modifier = Modifier.padding(top = Spacing.small), horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        itemsIndexed(genres, key = { _, genre -> genre.name }) { index, genre ->
            TvPlate(
                title = genre.name,
                posterPath = genre.art?.let(::File),
                onOpen = { onOpenGenre(genre.name) },
                modifier = Modifier.width(DeptTileWidth).let { if (index == focusAt && focus != null) it.focusRequester(focus) else it },
                caption = titleCountLabel(genre.count),
            )
        }
    }
}

/**
 * Scrolls [focusAt] on screen before asking for its focus, the way [TvWall]
 * does for its own grid: a lazy row composes only what is near the
 * viewport, so a [FocusRequester] beyond it has nothing to attach to until
 * scrolling has laid that stop out.
 */
private suspend fun scrollThenFocus(
    state: LazyListState,
    focusAt: Int?,
    focus: FocusRequester?,
    takesFocus: Boolean,
) {
    if (focusAt == null || focus == null || !takesFocus) return
    state.scrollToItem(focusAt)
    snapshotFlow { state.layoutInfo.visibleItemsInfo }.first { info -> info.any { it.index == focusAt } }
    focus.requestFocus()
}

/** `1 title` / `12 titles` — the same count line `TvLists`' own rows use for a list. */
private fun titleCountLabel(count: Int): String = "$count ${if (count == 1) "title" else "titles"}"
