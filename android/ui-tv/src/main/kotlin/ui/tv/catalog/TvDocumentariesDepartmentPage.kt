package ui.tv.catalog

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.layout.LazyLayoutCacheWindow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.unit.dp
import catalog.Department
import catalog.DocumentariesDepartment
import catalog.documentariesLineOf
import catalog.keyOf
import catalog.resumeCardsOf
import kotlinx.coroutines.flow.first as firstOf
import model.WatchSnapshot
import ui.tv.TvTextRow
import ui.tv.chrome.LocalTvPagePadding

/** How far past the page's own viewport a section stays composed — [TvMoviesDepartmentPage]'s own value, this page runs to as many rows. */
private val DocumentariesCacheWindow = 900.dp

/**
 * The Documentaries department's own front page — `renderDocumentariesDept`'s
 * television twin: [TvDepartmentHero] (never a link — the web's own
 * `leadHref: null`, nothing here comes from a provider), what is underway,
 * one row per hand-set category, what arrived, one row per folder, then
 * whatever was uploaded on its own. A plate here plays on OK rather than
 * opening a title page, the same as every other row the web wires to
 * `cx.play` — the recorded difference that used to leave this shelf a plain
 * wall with no category rows at all goes with this page.
 *
 * `null` [dept] (an empty library) is [DepartmentOrShelfWall]'s own concern;
 * this composable is never reached for one.
 */
@Composable
internal fun TvDocumentariesDepartmentPage(
    dept: DocumentariesDepartment,
    watch: WatchSnapshot,
    onOpenCollection: (key: String) -> Unit,
    onPlay: (setId: String) -> Unit,
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
    listState: LazyListState = rememberLazyListState(cacheWindow = LazyLayoutCacheWindow(ahead = DocumentariesCacheWindow, behind = DocumentariesCacheWindow)),
) {
    val (positions, watchedIds) = rememberWatchMarks(watch)
    val resumeCards = remember(dept, watch, heldIds) { resumeCardsOf(dept.continuing, emptyList(), watch, heldIds) }
    val focus = remember { FocusRequester() }
    var lastSection by rememberSaveable { mutableStateOf<String?>(null) }
    val sections =
        remember(dept, resumeCards) {
            buildList {
                add(DeptSection(ContinueSection, resumeCards.map { it.set.setId }))
                dept.categories.forEachIndexed { i, row -> add(DeptSection(categorySection(i), row.units.map(::keyOf))) }
                add(DeptSection(RecentlyAddedSection, dept.recentlyAdded.map { it.setId }))
                dept.collections.forEachIndexed { i, group -> add(DeptSection(groupSection(i), group.preview.map { it.setId })) }
                add(DeptSection(StandaloneSection, dept.singles.map { it.setId }))
            }
        }
    val target = remember(sections, restoreKey, lastSection) { documentariesDeptTargetOf(sections, restoreKey, lastSection) }
    // Exactly the rows the list below draws, in order, so the arrival's scroll index lands on its row.
    val included = remember(sections) { sections.filter { it.stops.isNotEmpty() }.map { it.id } }
    val takesFocus = LocalTakesArrivalFocus.current
    var sectionInView by remember { mutableStateOf(false) }
    LaunchedEffect(target, takesFocus) {
        sectionInView = false
        if (!takesFocus) return@LaunchedEffect
        val itemIndex = included.indexOf(target.section) + 1 // the hero is item 0.
        listState.scrollToItem(itemIndex)
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.firstOf { info -> info.any { it.index == itemIndex } }
        sectionInView = true
    }
    val rowTakesFocus = takesFocus && sectionInView

    val pagePadding = LocalTvPagePadding.current
    TvPage(takesArrivalFocus = takesFocus) {
        LazyColumn(
            state = listState,
            // Scoped to this list alone — [TvMoviesDepartmentPage]'s own
            // doc on why this coexists with the explicit requests above.
            modifier = Modifier.fillMaxSize().focusRestorer(fallback = focus),
            contentPadding = PaddingValues(top = pagePadding.top, bottom = pagePadding.bottom),
        ) {
            item(key = "hero") {
                Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                    TvDepartmentHero(title = Department.DOCUMENTARIES.label, line = documentariesLineOf(dept), lead = dept.lead)
                }
            }
            if (ContinueSection in included) {
                item(key = ContinueSection) {
                    Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                        DeptResumeRow(
                            "Continue watching",
                            resumeCards,
                            onPlay,
                            focusAt = target.stopAt(ContinueSection),
                            focus = focus,
                            takesFocus = rowTakesFocus,
                            onSectionFocused = { lastSection = ContinueSection },
                        )
                    }
                }
            }
            for ((i, row) in dept.categories.withIndex()) {
                val section = categorySection(i)
                if (section in included) {
                    item(key = section) {
                        Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                            DeptEntryRow(
                                row.title,
                                row.units,
                                positions,
                                watchedIds,
                                onOpenTitle = onPlay,
                                onOpenCollection = onOpenCollection,
                                heldIds = heldIds,
                                focusAt = target.stopAt(section),
                                focus = focus,
                                takesFocus = rowTakesFocus,
                                onSectionFocused = { lastSection = section },
                            )
                        }
                    }
                }
            }
            if (RecentlyAddedSection in included) {
                item(key = RecentlyAddedSection) {
                    Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                        DeptRow(
                            "Recently added",
                            dept.recentlyAdded,
                            onPlay,
                            focusAt = target.stopAt(RecentlyAddedSection),
                            focus = focus,
                            takesFocus = rowTakesFocus,
                            heldIds = heldIds,
                            onSectionFocused = { lastSection = RecentlyAddedSection },
                        )
                    }
                }
            }
            for ((i, group) in dept.collections.withIndex()) {
                val section = groupSection(i)
                val hasMore = group.collection.count > group.preview.size
                if (section in included) {
                    item(key = section) {
                        Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                            DeptRow(
                                group.collection.name,
                                group.preview,
                                onPlay,
                                focusAt = target.stopAt(section),
                                focus = focus,
                                takesFocus = rowTakesFocus,
                                heldIds = heldIds,
                                onSectionFocused = { lastSection = section },
                                trailing = {
                                    if (hasMore) {
                                        TvTextRow(text = "All ${group.collection.count} →", onClick = { onOpenCollection(group.collection.key) })
                                    }
                                },
                            )
                        }
                    }
                }
            }
            if (StandaloneSection in included) {
                item(key = StandaloneSection) {
                    Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                        DeptRow(
                            "Standalone documentaries",
                            dept.singles,
                            onPlay,
                            focusAt = target.stopAt(StandaloneSection),
                            focus = focus,
                            takesFocus = rowTakesFocus,
                            heldIds = heldIds,
                            onSectionFocused = { lastSection = StandaloneSection },
                        )
                    }
                }
            }
        }
    }
}
