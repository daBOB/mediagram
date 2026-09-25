package ui.tv.catalog

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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import catalog.CollectionRow
import catalog.Division
import catalog.rowsOf
import catalog.watchedFractionOf
import designsystem.Overscan
import designsystem.Spacing
import model.Kind
import model.WatchSnapshot
import ui.tv.TvTextRow

/**
 * One season's episodes — the television twin of the phone's
 * `SeasonScreen`, in the same rows [TvCollectionRows] draws for a whole
 * show or course: a season is just the one division the wall's plate stood
 * for, so it is shown the same way.
 */
@Composable
fun TvSeason(
    division: Division,
    watch: WatchSnapshot,
    onOpenTitle: (setId: String) -> Unit,
    restoreKey: String? = null,
) {
    val rows = remember(division) { rowsOf(listOf(division)) }
    TvCollectionRows(rows, watch, onOpenTitle, restoreKey, header = null)
}

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
    val positions = remember(watch) { watch.progress.associateBy { it.setId } }
    val watchedIds = remember(watch) { watch.watched.mapTo(HashSet()) { it.setId } }
    val listState = rememberLazyListState()
    val focus = remember { FocusRequester() }
    val opens = { row: CollectionRow -> row is CollectionRow.Item && row.set.kind != Kind.DOCUMENT }
    val focusIndex =
        remember(rows, restoreKey) {
            rows.indexOfFirst { opens(it) && (it as CollectionRow.Item).set.setId == restoreKey }.takeIf { it >= 0 }
                ?: rows.indexOfFirst(opens).takeIf { it >= 0 }
        }
    val firstOpen = rows.indexOfFirst(opens)
    val offset = if (header != null) 1 else 0

    // A collection of nothing but documents has nothing to land on; Back
    // still leaves it.
    LaunchedEffect(focusIndex, restoreKey) {
        if (focusIndex != null) {
            // The first row scrolls the page to its very top, so the header
            // above it is on screen rather than scrolled past.
            listState.scrollToItem(if (focusIndex == firstOpen) 0 else focusIndex + offset)
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
                is CollectionRow.Heading -> TvSectionHeading(row.title, Modifier.padding(start = indentOf(row.depth), top = Spacing.medium))
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
 * fail. It takes no focus at all, the remote's way of greying a line out:
 * the D-pad passes over it to the next thing that opens.
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
            TvQuietLine(text)
            TvQuietLine(DocumentReason)
        } else {
            TvTextRow(text = text, onClick = { onOpenTitle(row.set.setId) }, modifier = Modifier.fillMaxWidth(), focusRequester = focus)
            progress?.let { TvProgressRule(fraction = it, modifier = Modifier.padding(top = Spacing.small)) }
        }
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
