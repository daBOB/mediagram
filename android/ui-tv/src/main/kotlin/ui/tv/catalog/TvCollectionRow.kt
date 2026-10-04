package ui.tv.catalog

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import designsystem.TvTypeScale
import model.Kind
import ui.tv.TvFocus
import ui.tv.TvTextRow

/**
 * One of [TvCollectionRows]' rows — a folder's heading, or an episode or
 * lesson to play — shared with a show's own Episodes tab, which lists one
 * season the same way. [focus] is the requester a caller lands the remote
 * on, for the one row that takes it.
 */
@Composable
internal fun TvCollectionRow(
    row: CollectionRow,
    marks: WatchMarks,
    heldIds: Set<String>,
    onPlay: (setId: String) -> Unit,
    focus: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    when (row) {
        is CollectionRow.Heading -> TvSectionHeading(row.title, modifier.padding(start = indentOf(row.depth)))
        is CollectionRow.Item ->
            ItemRow(
                row = row,
                progress = watchedFractionOf(marks.positions[row.set.setId]),
                watched = row.set.setId in marks.watchedIds,
                held = row.set.setId in heldIds,
                onPlay = onPlay,
                focus = focus,
                modifier = modifier,
            )
    }
}

/**
 * One lesson or episode to play, or one document, as the phone's own row:
 * the tick before the title so a column of them reads at a glance, and a
 * progress rule along its foot, and the offline badge under that for an
 * episode or lesson this device holds.
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
    held: Boolean,
    onPlay: (setId: String) -> Unit,
    focus: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val text = "${row.position}. ${if (watched) "✓ " else ""}${row.set.title}"
    Column(modifier = modifier.fillMaxWidth().padding(start = indentOf(row.depth))) {
        if (row.set.kind == Kind.DOCUMENT) {
            DocumentRow(text, focus)
        } else {
            TvTextRow(text = text, onClick = { onPlay(row.set.setId) }, modifier = Modifier.fillMaxWidth(), focusRequester = focus)
            TvItemMarks(progress, held)
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
    // Never omitted — see the same doc on `TvResumeCard`'s own `ownRequester`.
    val ownRequester = remember { FocusRequester() }
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .focusRequester(focus ?: ownRequester)
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
internal fun rowKeyOf(
    row: CollectionRow,
    index: Int,
): String =
    when (row) {
        is CollectionRow.Heading -> "heading-$index-${row.title}"
        is CollectionRow.Item -> row.set.setId
    }

private fun indentOf(depth: Int) = (depth * IndentStep).dp

private const val IndentStep = 24
