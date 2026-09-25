package ui.tv.catalog

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.CollectionRow
import catalog.watchedFractionOf
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import kotlinx.coroutines.flow.first
import model.Kind
import model.WatchSnapshot
import ui.tv.TvFocus
import ui.tv.TvTextRow

/**
 * What is inside a show or a course, as the phone lists it: each folder's
 * heading, indented as deep as it sits, and its episodes or lessons under
 * it — a course runs from one folder deep to four, and a viewer reads the
 * indent rather than headings that each repeat their parents.
 *
 * The remote lands on the first thing that can be opened, or on the set
 * [restoreKey] names when coming back from its title. [header] is whatever
 * stands above the rows and scrolls away with them.
 */
@Composable
internal fun TvCollectionRows(
    rows: List<CollectionRow>,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    restoreKey: String?,
    header: (@Composable () -> Unit)?,
) {
    val (positions, watchedIds) = rememberWatchMarks(watch)
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

    LaunchedEffect(focusIndex, restoreKey) {
        if (focusIndex != null) {
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
            when (row) {
                is CollectionRow.Heading -> TvSectionHeading(row.title, Modifier.padding(start = indentOf(row.depth)))
                is CollectionRow.Item ->
                    ItemRow(
                        row = row,
                        progress = watchedFractionOf(positions[row.set.setId]),
                        watched = row.set.setId in watchedIds,
                        onOpenTitle = onOpenTitle,
                        focus = focus.takeIf { index == focusIndex },
                    )
            }
        }
    }
}

/**
 * One lesson or episode to open, or one document, as the phone's own row:
 * the tick before the title so a column of them reads at a glance, and a
 * progress rule along its foot.
 *
 * A document is shown and not opened — nothing here can display a handout,
 * so the row says what it is rather than offering a press that could only
 * fail. The remote can still rest on it — a folder of nothing but
 * handouts must have somewhere to land — but a press there does nothing,
 * and it reads as disabled, the way a greyed-out menu item does.
 */
@Composable
private fun ItemRow(
    row: CollectionRow.Item,
    progress: Float?,
    watched: Boolean,
    onOpenTitle: (setId: String) -> Unit,
    focus: FocusRequester?,
) {
    val text = "${row.position}. ${if (watched) "✓ " else ""}${row.set.title}"
    Column(modifier = Modifier.fillMaxWidth().padding(start = indentOf(row.depth))) {
        if (row.set.kind == Kind.DOCUMENT) {
            DocumentRow(text, focus)
        } else {
            TvTextRow(text = text, onClick = { onOpenTitle(row.set.setId) }, modifier = Modifier.fillMaxWidth(), focusRequester = focus)
            progress?.let { TvProgressRule(fraction = it, modifier = Modifier.padding(top = Spacing.small)) }
        }
    }
}

@Composable
private fun DocumentRow(
    text: String,
    focus: FocusRequester?,
) {
    // Read from the focus state itself, as TvTextRow does: a row focused
    // on arrival never hears a focus interaction and would hold the remote
    // while drawn as if it did not.
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .let { if (focus != null) it.focusRequester(focus) else it }
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .semantics(mergeDescendants = true) { disabled() },
    ) {
        Text(
            text = text,
            style = TvFocus.textStyle(TvTypeScale.body.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), focused),
        )
        TvQuietLine(DocumentReason)
    }
}

/** Said under a document's own name, the way a disabled menu item says why — the phone's sentence, for this device. */
internal const val DocumentReason = "Document — the television cannot open one yet"

/** A set id is unique and a heading is not — two courses can both have a "Grundlagen" — so a heading is keyed by where it sits. */
private fun rowKeyOf(
    row: CollectionRow,
    index: Int,
): String =
    when (row) {
        is CollectionRow.Heading -> "heading-$index-${row.title}"
        is CollectionRow.Item -> row.set.setId
    }

private fun indentOf(depth: Int) = (depth * IndentStep).dp

private const val IndentStep = 24
