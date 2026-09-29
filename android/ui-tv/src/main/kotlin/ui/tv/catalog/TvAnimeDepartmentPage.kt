package ui.tv.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import catalog.AnimeDepartment
import catalog.Entry
import catalog.animeLineOf
import catalog.keyOf
import catalog.resumeCardsOf
import designsystem.Spacing
import model.WatchSnapshot
import ui.tv.chrome.LocalTvPagePadding

/**
 * The Anime department's own front page — `anime-department.js`'s own
 * `renderAnimeDept`, television twin: [TvDepartmentHero], Continue watching,
 * then every show and every film as one [TvWall] with two headings sections
 * — "Series" then "Films". A show's plate opens the show; a film's opens
 * the title page, never plays directly — the locked, deliberate difference
 * from the web's own direct play, kept exactly as
 * [ui.tv.catalog.openEntry] already reads it for every other wall.
 */
@Composable
internal fun TvAnimeDepartmentPage(
    dept: AnimeDepartment,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onPlay: (setId: String) -> Unit,
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val (positions, watchedIds) = rememberWatchMarks(watch)
    val resumeCards = remember(dept, watch, heldIds) { resumeCardsOf(dept.continuing, dept.nextUp, watch, heldIds) }
    val rowFocus = remember { FocusRequester() }
    var lastSection by rememberSaveable { mutableStateOf<String?>(null) }
    val target = remember(resumeCards, restoreKey, lastSection) { animeDeptTargetOf(resumeCards, restoreKey, lastSection) }
    val takesFocus = LocalTakesArrivalFocus.current
    LaunchedEffect(target, takesFocus) {
        if (takesFocus && target != null) rowFocus.requestFocus()
    }

    // Keyed by `keyOf` per entry, shows under `ANIME/…`, films under their
    // own set id — distinct namespaces, so the two never collide in one wall
    // (this page's own risk the shared key already rules out).
    val items: List<Entry> = remember(dept) { dept.shows + dept.films.map(Entry::Film) }
    val headings =
        remember(dept) {
            buildMap {
                if (dept.shows.isNotEmpty()) put(0, "Series")
                if (dept.films.isNotEmpty()) put(dept.shows.size, "Films")
            }
        }
    val pagePadding = LocalTvPagePadding.current

    TvPage(takesArrivalFocus = takesFocus) {
        CompositionLocalProvider(LocalTakesArrivalFocus provides (takesFocus && target == null)) {
            TvWall(
                items = items,
                key = ::keyOf,
                restoreKey = restoreKey,
                onOpen = { entry -> openEntry(entry, onOpenTitle, onOpenCollection) },
                gridState = gridState,
                headings = headings,
                header = {
                    Column {
                        Box(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                            TvDepartmentHero(title = "Anime", line = animeLineOf(dept), lead = dept.lead)
                        }
                        Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end, bottom = Spacing.medium)) {
                            DeptResumeRow(
                                "Continue watching",
                                resumeCards,
                                onPlay,
                                focusAt = target?.second?.takeIf { target.first == "continue" },
                                focus = rowFocus,
                                takesFocus = takesFocus,
                                onSectionFocused = { lastSection = "continue" },
                            )
                        }
                    }
                },
                plate = { entry, modifier, onOpen -> TvEntryPlate(entry, positions, watchedIds, onOpen, modifier, heldIds) },
            )
        }
    }
}
