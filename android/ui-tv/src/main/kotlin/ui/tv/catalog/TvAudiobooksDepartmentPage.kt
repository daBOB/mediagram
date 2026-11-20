package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import catalog.AudiobooksDepartment
import catalog.Department
import catalog.Entry
import catalog.audiobooksLineOf
import catalog.keyOf
import kotlinx.coroutines.flow.first as firstOf
import model.WatchSnapshot
import ui.tv.catalog.home.TvBandHeading
import ui.tv.chrome.LocalTvPagePadding
import ui.tv.rememberStableRequester

/** How far past the page's own viewport a section stays composed — [TvDocumentariesDepartmentPage]'s own value. */
private val AudiobooksCacheWindow = 900.dp

/** "All audiobooks", the one section drawn as a grid rather than a row. */
private const val AllSection = "all"

/**
 * The Audiobooks department's front page — `audiobooks-department.js`'s
 * television twin: [TvDepartmentHero] with no lead (a book has no backdrop),
 * Continue listening, one row per genre, Recently added, then every book by
 * title as a grid of [Columns] a line, the web's wrapping grid. Every plate
 * plays on OK, as the web wires each to `cx.play`.
 */
@Composable
internal fun TvAudiobooksDepartmentPage(
    dept: AudiobooksDepartment,
    watch: WatchSnapshot,
    onPlay: (setId: String) -> Unit,
    restoreKey: String? = null,
    heldIds: Set<String> = emptySet(),
    listState: LazyListState = rememberLazyListState(cacheWindow = LazyLayoutCacheWindow(ahead = AudiobooksCacheWindow, behind = AudiobooksCacheWindow)),
) {
    val (positions, watchedIds) = rememberWatchMarks(watch)
    val focus = remember { FocusRequester() }
    var lastSection by rememberSaveable { mutableStateOf<String?>(null) }
    val sections =
        remember(dept) {
            buildList {
                add(DeptSection(ContinueSection, dept.resumeCards.map { it.set.setId }))
                dept.categories.forEachIndexed { i, row -> add(DeptSection(categorySection(i), row.units.map(::keyOf))) }
                add(DeptSection(RecentlyAddedSection, dept.recentlyAdded.map { it.setId }))
                add(DeptSection(AllSection, dept.books.map { it.setId }))
            }
        }
    val target = remember(sections, restoreKey, lastSection) { documentariesDeptTargetOf(sections, restoreKey, lastSection) }
    // Exactly the sections the list below draws, in order; "All" is a heading item and then one item per line.
    val included = remember(sections) { sections.filter { it.stops.isNotEmpty() }.map { it.id } }
    val lines = remember(dept) { dept.books.chunked(Columns) }
    val takesFocus = LocalTakesArrivalFocus.current
    var sectionInView by remember { mutableStateOf(false) }
    LaunchedEffect(target, takesFocus) {
        sectionInView = false
        if (!takesFocus) return@LaunchedEffect
        val section = included.indexOf(target.section) + 1 // the hero is item 0.
        val itemIndex = if (target.section == AllSection) section + 1 + target.stop / Columns else section
        listState.scrollToItem(itemIndex)
        snapshotFlow { listState.layoutInfo.visibleItemsInfo }.firstOf { info -> info.any { it.index == itemIndex } }
        sectionInView = true
    }
    // A grid line has no row of its own to scroll first: once the list has it on screen, its plate's requester is attached.
    LaunchedEffect(sectionInView, target) { if (sectionInView && target.section == AllSection) focus.requestFocus() }
    val rowTakesFocus = takesFocus && sectionInView
    val pagePadding = LocalTvPagePadding.current
    val sidePadding = Modifier.padding(start = pagePadding.start, end = pagePadding.end)

    TvPage(takesArrivalFocus = takesFocus) {
        GlidesOnEntry(listState) { glide ->
            LazyColumn(
                state = listState,
                modifier = glide.fillMaxSize().toTopWhenLeftForTheBar { listState.animateScrollToItem(0) }.focusRestorer(fallback = focus),
                contentPadding = PaddingValues(top = pagePadding.top, bottom = pagePadding.bottom),
            ) {
                item(key = "hero") {
                    Column(sidePadding) { TvDepartmentHero(title = Department.AUDIOBOOKS.label, line = audiobooksLineOf(dept), lead = null) }
                }
                if (ContinueSection in included) {
                    item(key = ContinueSection) {
                        Column(sidePadding) {
                            DeptResumeRow(
                                "Continue listening", dept.resumeCards, onPlay,
                                focusAt = target.stopAt(ContinueSection), focus = focus, takesFocus = rowTakesFocus,
                                onSectionFocused = { lastSection = ContinueSection },
                            )
                        }
                    }
                }
                val rows = dept.categories.mapIndexed { i, row -> Triple(categorySection(i), row.title, row.units) } +
                    Triple(RecentlyAddedSection, "Recently added", dept.recentlyAdded.map(Entry::Film))
                for ((section, title, units) in rows) {
                    if (section !in included) continue
                    item(key = section) {
                        Column(sidePadding) {
                            DeptEntryRow(
                                title, units, positions, watchedIds,
                                onOpenTitle = onPlay, onOpenCollection = {}, heldIds = heldIds,
                                focusAt = target.stopAt(section), focus = focus, takesFocus = rowTakesFocus,
                                onSectionFocused = { lastSection = section },
                            )
                        }
                    }
                }
                item(key = AllSection) {
                    Box(sidePadding.padding(top = DeptRowTop)) { TvBandHeading(title = "All audiobooks", count = null) }
                }
                itemsIndexed(lines, key = { _, line -> "$AllSection/${line.first().setId}" }) { lineIndex, line ->
                    Row(
                        modifier =
                            sidePadding
                                .fillMaxWidth()
                                .padding(top = if (lineIndex == 0) RowHeadBelow else StripGap)
                                .onFocusChanged { if (it.hasFocus) lastSection = AllSection },
                        horizontalArrangement = Arrangement.spacedBy(StripGap),
                    ) {
                        line.forEachIndexed { i, book ->
                            val stop = lineIndex * Columns + i
                            TvEntryPlate(
                                Entry.Film(book), positions, watchedIds,
                                onOpen = { onPlay(book.setId) },
                                modifier =
                                    Modifier
                                        .weight(1f)
                                        .focusRequester(rememberStableRequester(focus.takeIf { target.section == AllSection && target.stop == stop })),
                                heldIds = heldIds,
                            )
                        }
                        // A short last line keeps its plates the width of the ones above.
                        repeat(Columns - line.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}
