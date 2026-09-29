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
import catalog.Entry
import catalog.ShowsDepartment
import catalog.firstItemOf
import catalog.resumeCardsOf
import catalog.showsLineOf
import designsystem.Spacing
import model.WatchSnapshot
import ui.tv.catalog.home.TvBandHeading
import ui.tv.chrome.LocalTvPagePadding

/**
 * The Series or Tutorials department's own front page — `renderShowsDept`'s
 * television twin: [TvDepartmentHero], what is underway, one row per
 * hand-set category (Tutorials only — a Series shelf is never categorised),
 * Popular and New episodes (both empty below a dozen shows —
 * [ShowsDepartment]'s own gate), and finally every show the shelf holds, as
 * one wall.
 *
 * [restoreKey] favours a header row over [TvWall]'s own plate-0 default —
 * [showsDeptTargetOf]'s own reading, `null` from it meaning "hand it to the
 * wall itself", which the [LocalTakesArrivalFocus] this composes around the
 * wall then leaves alone.
 */
@Composable
internal fun TvShowsDepartmentPage(
    label: String,
    unit: String,
    dept: ShowsDepartment,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    onOpenCollection: (key: String) -> Unit,
    onPlay: (setId: String) -> Unit,
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
    gridState: LazyGridState = rememberLazyGridState(),
) {
    val (positions, watchedIds) = rememberWatchMarks(watch)
    val resumeCards = remember(dept, watch, heldIds) { resumeCardsOf(dept.underway.continues, dept.underway.nextUp, watch, heldIds) }
    val lead = dept.lead
    val cover = lead?.let(::leadCover)
    val rowFocus = remember { FocusRequester() }
    var lastSection by rememberSaveable { mutableStateOf<String?>(null) }
    val target = remember(dept, resumeCards, restoreKey, lastSection) { showsDeptTargetOf(dept, resumeCards, restoreKey, lastSection) }
    val takesFocus = LocalTakesArrivalFocus.current
    LaunchedEffect(target, takesFocus) {
        if (takesFocus && target != null) rowFocus.requestFocus()
    }

    TvPage(takesArrivalFocus = takesFocus) {
        // Suppressed only while a header row above the wall is this page's
        // own target — otherwise TvWall's plate-0 default is exactly what a
        // viewer opening a show further down expects.
        CompositionLocalProvider(LocalTakesArrivalFocus provides (takesFocus && target == null)) {
            TvWall(
                items = dept.all,
                key = Entry.Collection::key,
                restoreKey = restoreKey,
                onOpen = { entry -> onOpenCollection(entry.key) },
                gridState = gridState,
                header = {
                    Column {
                        Box(modifier = Modifier.padding(start = LocalTvPagePadding.current.start, end = LocalTvPagePadding.current.end)) {
                            TvDepartmentHero(
                                title = label,
                                line = showsLineOf(dept, label, unit),
                                lead = cover,
                                // The show's own name, not whichever episode
                                // happened to lead — the web's own `lead?.show`.
                                leadName = lead?.let { firstItemOf(it.divisions) }?.show,
                            )
                        }
                        Column(
                            modifier =
                                Modifier
                                    .padding(start = LocalTvPagePadding.current.start, end = LocalTvPagePadding.current.end, bottom = Spacing.medium),
                        ) {
                            DeptResumeRow(
                                if (label == "Series") "Continue your series" else "Continue your courses",
                                resumeCards,
                                onPlay,
                                focusAt = target?.second?.takeIf { target.first == "underway" },
                                focus = rowFocus,
                                takesFocus = takesFocus,
                                onSectionFocused = { lastSection = "underway" },
                            )
                            for ((i, row) in dept.categories.withIndex()) {
                                DeptEntryRow(
                                    row.title,
                                    row.units,
                                    positions,
                                    watchedIds,
                                    onOpenTitle,
                                    onOpenCollection,
                                    heldIds,
                                    focusAt = target?.second?.takeIf { target.first == "category:$i" },
                                    focus = rowFocus,
                                    takesFocus = takesFocus,
                                    onSectionFocused = { lastSection = "category:$i" },
                                )
                            }
                            DeptEntryRow(
                                "Popular",
                                dept.popular,
                                positions,
                                watchedIds,
                                onOpenTitle,
                                onOpenCollection,
                                heldIds,
                                focusAt = target?.second?.takeIf { target.first == "popular" },
                                focus = rowFocus,
                                takesFocus = takesFocus,
                                onSectionFocused = { lastSection = "popular" },
                            )
                            DeptEntryRow(
                                "New episodes",
                                dept.newEpisodes,
                                positions,
                                watchedIds,
                                onOpenTitle,
                                onOpenCollection,
                                heldIds,
                                focusAt = target?.second?.takeIf { target.first == "newEpisodes" },
                                focus = rowFocus,
                                takesFocus = takesFocus,
                                onSectionFocused = { lastSection = "newEpisodes" },
                            )
                        }
                        Box(modifier = Modifier.padding(start = LocalTvPagePadding.current.start, end = LocalTvPagePadding.current.end)) {
                            TvBandHeading(title = "Every show", count = dept.all.size)
                        }
                    }
                },
                plate = { entry, modifier, onOpen -> TvEntryPlate(entry, positions, watchedIds, onOpen, modifier, heldIds) },
            )
        }
    }
}

/** A show's own first episode, standing in for it on a hero built for [model.MediaSet] — the same swap `SeriesPageState.leadOf` makes. */
private fun leadCover(entry: Entry.Collection): model.MediaSet? = firstItemOf(entry.divisions)?.copy(setId = entry.key)
