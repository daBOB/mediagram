package ui.tv.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import catalog.Entry
import catalog.ShowsDepartment
import catalog.firstItemOf
import designsystem.Spacing
import model.MediaSet
import model.WatchSnapshot

/**
 * The Series or Tutorials department's own front page — `renderShowsDept`'s
 * television twin: a hero for the most popular show with a backdrop, then
 * what is underway, Popular and New episodes (both empty below a dozen
 * shows — [ShowsDepartment]'s own gate), and finally every show the shelf
 * holds, as one wall rather than a separate link: unlike Movies, a show's
 * own card is already the whole of what "all" would add, so there is no
 * second flat page to send "All N" to.
 *
 * Arrival focus and [restoreKey] favour the hero, then a header row, over
 * [TvWall]'s own plate-0 default — [showsDeptTargetOf]'s own reading, `null`
 * from it meaning "hand it to the wall itself", which the
 * [LocalTakesArrivalFocus] this composes around the wall then leaves alone.
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
    val lead = dept.lead
    val cover = lead?.let(::leadCover)
    val heroFocus = remember { FocusRequester() }
    val rowFocus = remember { FocusRequester() }
    val target = remember(dept, underwayEntries, cover, restoreKey) { showsDeptTargetOf(dept, underwayEntries, cover != null, restoreKey) }
    val takesFocus = LocalTakesArrivalFocus.current
    LaunchedEffect(target, takesFocus) {
        if (!takesFocus) return@LaunchedEffect
        when (target?.first) {
            "hero" -> heroFocus.requestFocus()
            "underway", "popular", "newEpisodes" -> rowFocus.requestFocus()
            else -> Unit // null: the wall below claims it instead.
        }
    }

    TvPage(takesArrivalFocus = takesFocus) {
        // Suppressed only while the hero or a header row above the wall is
        // this page's own target — otherwise TvWall's plate-0 default is
        // exactly what a viewer opening a show further down expects.
        CompositionLocalProvider(LocalTakesArrivalFocus provides (takesFocus && target == null)) {
            TvWall(
                items = dept.all,
                key = Entry.Collection::key,
                restoreKey = restoreKey,
                onOpen = { entry -> onOpenCollection(entry.key) },
                header = {
                    Column {
                        if (cover != null && lead != null) {
                            TvCoverStory(
                                films = listOf(cover),
                                onPlay = { onOpenCollection(lead.key) },
                                onOpenTitle = { onOpenCollection(lead.key) },
                                kicker = DeptKicker,
                                arrivalFocus = heroFocus,
                            )
                        }
                        Column(modifier = Modifier.padding(bottom = Spacing.medium)) {
                            DeptEntryRow(
                                "Continue",
                                underwayEntries,
                                positions,
                                watchedIds,
                                onOpenTitle,
                                onOpenCollection,
                                heldIds,
                                focusAt = target?.second?.takeIf { target.first == "underway" },
                                focus = rowFocus,
                                takesFocus = takesFocus,
                            )
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
                            )
                        }
                        TvCountedHeading("Every show", dept.all.size)
                    }
                },
                plate = { entry, modifier, onOpen -> TvEntryPlate(entry, positions, watchedIds, onOpen, modifier, heldIds) },
            )
        }
    }
}

/** A show's own first episode, standing in for it on a hero built for [MediaSet] — the same swap `SeriesPageState.leadOf` makes. */
private fun leadCover(entry: Entry.Collection): MediaSet? = firstItemOf(entry.divisions)?.copy(setId = entry.key)
