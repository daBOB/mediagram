package ui.tv.catalog

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
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
import ui.catalog.rememberRowState
import ui.tv.catalog.home.TvBandHeading

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
    onSectionFocused: (() -> Unit)? = null,
    // Documentaries' own "All N →", beside a folder row's own heading —
    // every other caller leaves this at its default, drawing no trailing at
    // all, the same as the web's own `deptRow` with no `more` link.
    trailing: @Composable () -> Unit = {},
) {
    if (films.isEmpty()) return
    Box(Modifier.padding(top = Spacing.large)) { TvBandHeading(title = title, count = null, trailing = trailing) }
    val inRow = remember { mutableStateOf(false) }
    val state = rememberRowState(films.map(MediaSet::setId), rememberLazyListState(cacheWindow = remember { LazyLayoutCacheWindow(ahead = DeptCacheAhead, behind = DeptCacheBehind) }), inUse = { inRow.value })
    LaunchedEffect(focusAt, takesFocus) { scrollThenFocus(state, focusAt, focus, takesFocus) }
    LazyRow(
        state = state,
        modifier =
            Modifier
                .padding(top = Spacing.small)
                .onFocusChanged { s ->
                    inRow.value = s.hasFocus
                    if (s.hasFocus) onSectionFocused?.invoke()
                },
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        itemsIndexed(films, key = { _, set -> set.setId }) { index, set ->
            // Never omitted — see the same doc on `TvResumeCard`'s own `ownRequester`.
            val ownRequester = remember { FocusRequester() }
            TvPlate(
                title = set.title,
                posterPath = set.posterPath?.let(::File),
                onOpen = { onOpenTitle(set.setId) },
                modifier = Modifier.width(DeptTileWidth).focusRequester(if (index == focusAt) focus ?: ownRequester else ownRequester),
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
    onSectionFocused: (() -> Unit)? = null,
) {
    if (entries.isEmpty()) return
    Box(Modifier.padding(top = Spacing.large)) { TvBandHeading(title = title, count = null) }
    val inRow = remember { mutableStateOf(false) }
    val state = rememberRowState(entries.map { keyOf(it) }, rememberLazyListState(cacheWindow = remember { LazyLayoutCacheWindow(ahead = DeptCacheAhead, behind = DeptCacheBehind) }), inUse = { inRow.value })
    LaunchedEffect(focusAt, takesFocus) { scrollThenFocus(state, focusAt, focus, takesFocus) }
    LazyRow(
        state = state,
        modifier =
            Modifier
                .padding(top = Spacing.small)
                .onFocusChanged { s ->
                    inRow.value = s.hasFocus
                    if (s.hasFocus) onSectionFocused?.invoke()
                },
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        itemsIndexed(entries, key = { _, entry -> keyOf(entry) }) { index, entry ->
            // Never omitted — see the same doc on `TvResumeCard`'s own `ownRequester`.
            val ownRequester = remember { FocusRequester() }
            TvEntryPlate(
                entry = entry,
                positions = positions,
                watchedIds = watchedIds,
                onOpen = { openEntry(entry, onOpenTitle, onOpenCollection) },
                modifier = Modifier.width(DeptTileWidth).focusRequester(if (index == focusAt) focus ?: ownRequester else ownRequester),
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
    onSectionFocused: (() -> Unit)? = null,
) {
    val state = rememberLazyListState(cacheWindow = remember { LazyLayoutCacheWindow(ahead = DeptCacheAhead, behind = DeptCacheBehind) })
    LaunchedEffect(focusAt, takesFocus) { scrollThenFocus(state, focusAt, focus, takesFocus) }
    LazyRow(
        state = state,
        modifier =
            Modifier
                .padding(top = Spacing.small)
                .let { if (onSectionFocused != null) it.onFocusChanged { s -> if (s.hasFocus) onSectionFocused() } else it },
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        itemsIndexed(genres, key = { _, genre -> genre.name }) { index, genre ->
            // Never omitted — see the same doc on `TvResumeCard`'s own `ownRequester`.
            val ownRequester = remember { FocusRequester() }
            TvPlate(
                title = genre.name,
                posterPath = genre.art?.let(::File),
                onOpen = { onOpenGenre(genre.name) },
                modifier = Modifier.width(DeptTileWidth).focusRequester(if (index == focusAt) focus ?: ownRequester else ownRequester),
                caption = titleCountLabel(genre.count),
            )
        }
    }
}

/**
 * Scrolls [focusAt] on screen before asking for its focus, the way [TvWall]
 * does for its own grid: a lazy row composes only what is near the
 * viewport, so a [FocusRequester] beyond it has nothing to attach to until
 * scrolling has laid that stop out. Internal, not private: [DeptResumeRow]
 * (`TvDepartmentResumeRow.kt`) shares this same rule over its own cards
 * rather than a second copy of it.
 */
internal suspend fun scrollThenFocus(
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
