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
                add(DeptSection("continue", resumeCards.map { it.set.setId }))
                dept.categories.forEachIndexed { i, row -> add(DeptSection("category:$i", row.units.map(::keyOf))) }
                add(DeptSection("recentlyAdded", dept.recentlyAdded.map { it.setId }))
                dept.collections.forEachIndexed { i, group -> add(DeptSection("group:$i", group.preview.map { it.setId })) }
                add(DeptSection("standalone", dept.singles.map { it.setId }))
            }
        }
    val target = remember(sections, restoreKey, lastSection) { documentariesDeptTargetOf(sections, restoreKey, lastSection) }
    val included = remember(sections) { sections.filter { it.stops.isNotEmpty() }.map { it.name } }
    val takesFocus = LocalTakesArrivalFocus.current
    var sectionInView by remember { mutableStateOf(false) }
    LaunchedEffect(target, takesFocus) {
        sectionInView = false
        if (!takesFocus) return@LaunchedEffect
        val itemIndex = included.indexOf(target.first) + 1 // the hero is item 0.
        listState.scrollToItem(itemIndex)
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.firstOf { info -> info.any { it.index == itemIndex } }
        sectionInView = true
    }
    val rowTakesFocus = takesFocus && sectionInView

    val pagePadding = LocalTvPagePadding.current
    fun stopAt(section: String): Int? = target.second.takeIf { target.first == section }

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
            item(key = "continue") {
                Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                    DeptResumeRow(
                        "Continue watching",
                        resumeCards,
                        onPlay,
                        focusAt = stopAt("continue"),
                        focus = focus,
                        takesFocus = rowTakesFocus,
                        onSectionFocused = { lastSection = "continue" },
                    )
                }
            }
            for ((i, row) in dept.categories.withIndex()) {
                item(key = "category:$i") {
                    Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                        DeptEntryRow(
                            row.title,
                            row.units,
                            positions,
                            watchedIds,
                            onOpenTitle = onPlay,
                            onOpenCollection = onOpenCollection,
                            heldIds = heldIds,
                            focusAt = stopAt("category:$i"),
                            focus = focus,
                            takesFocus = rowTakesFocus,
                            onSectionFocused = { lastSection = "category:$i" },
                        )
                    }
                }
            }
            item(key = "recentlyAdded") {
                Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                    DeptRow(
                        "Recently added",
                        dept.recentlyAdded,
                        onPlay,
                        focusAt = stopAt("recentlyAdded"),
                        focus = focus,
                        takesFocus = rowTakesFocus,
                        heldIds = heldIds,
                        onSectionFocused = { lastSection = "recentlyAdded" },
                    )
                }
            }
            for ((i, group) in dept.collections.withIndex()) {
                val hasMore = group.collection.count > group.preview.size
                item(key = "group:$i") {
                    Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                        DeptRow(
                            group.collection.name,
                            group.preview,
                            onPlay,
                            focusAt = stopAt("group:$i"),
                            focus = focus,
                            takesFocus = rowTakesFocus,
                            heldIds = heldIds,
                            onSectionFocused = { lastSection = "group:$i" },
                            trailing = {
                                if (hasMore) {
                                    TvTextRow(text = "All ${group.collection.count} →", onClick = { onOpenCollection(group.collection.key) })
                                }
                            },
                        )
                    }
                }
            }
            item(key = "standalone") {
                Column(modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end)) {
                    DeptRow(
                        "Standalone documentaries",
                        dept.singles,
                        onPlay,
                        focusAt = stopAt("standalone"),
                        focus = focus,
                        takesFocus = rowTakesFocus,
                        heldIds = heldIds,
                        onSectionFocused = { lastSection = "standalone" },
                    )
                }
            }
        }
    }
}
