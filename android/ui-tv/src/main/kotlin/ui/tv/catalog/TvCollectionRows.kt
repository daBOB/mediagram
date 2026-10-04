package ui.tv.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import catalog.CollectionRow
import designsystem.Overscan
import designsystem.Spacing
import kotlinx.coroutines.flow.first
import model.Kind
import model.WatchSnapshot

/**
 * What is inside a show or a course, as the phone lists it: each folder's
 * heading, indented as deep as it sits, and its episodes or lessons under
 * it — a course runs from one folder deep to four, and a viewer reads the
 * indent rather than headings that each repeat their parents.
 *
 * A row plays rather than opening a title page first, as the phone's and
 * the web's (`course-view.js`'s `lessonRow`) do. The remote lands on the
 * first thing that can be played, or on the set [restoreKey] names when
 * coming back from the player. [header] is whatever
 * stands above the rows and scrolls away with them.
 */
@Composable
internal fun TvCollectionRows(
    rows: List<CollectionRow>,
    watch: WatchSnapshot,
    onPlay: (setId: String) -> Unit,
    restoreKey: String?,
    header: (@Composable () -> Unit)?,
    heldIds: Set<String> = emptySet(),
) {
    val marks = rememberWatchMarks(watch)
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val opens = { row: CollectionRow -> row is CollectionRow.Item && row.set.kind != Kind.DOCUMENT }
    val focusIndex =
        remember(rows, restoreKey) {
            rows.indexOfFirst { opens(it) && (it as CollectionRow.Item).set.setId == restoreKey }.takeIf { it >= 0 }
                ?: rows.indexOfFirst(opens).takeIf { it >= 0 }
                // Nothing here opens — a folder of handouts — so the first
                // document takes the remote rather than leaving it on nothing.
                ?: rows.indexOfFirst { it is CollectionRow.Item }.takeIf { it >= 0 }
        }
    val firstFocus = remember(rows) { rows.indexOfFirst(opens).takeIf { it >= 0 } ?: rows.indexOfFirst { it is CollectionRow.Item } }
    val offset = if (header != null) 1 else 0
    val takesFocus = LocalTakesArrivalFocus.current

    LaunchedEffect(focusIndex, restoreKey) {
        if (focusIndex != null && takesFocus) {
            val item = focusIndex + offset
            if (focusIndex == firstFocus) {
                // The first row scrolls the page to its very top, so the
                // header above it is on screen rather than scrolled past —
                // unless the header fills the screen and leaves that row
                // unlaid-out below it, with nothing for the focus to land on.
                listState.scrollToItem(0)
                val shown = snapshotFlow { listState.layoutInfo.visibleItemsInfo }.first { it.isNotEmpty() }
                if (shown.none { it.index == item }) listState.scrollToItem(item)
            } else {
                listState.scrollToItem(item)
            }
            focus.requestFocus()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        if (header != null) item(key = "header") { header() }
        itemsIndexed(rows, key = { index, row -> rowKeyOf(row, index) }) { index, row ->
            TvCollectionRow(row, marks, heldIds, onPlay, focus.takeIf { index == focusIndex })
        }
    }
}
