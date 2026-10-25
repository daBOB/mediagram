package ui.tv.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.CollectionRow
import catalog.watchedFractionOf
import designsystem.LocalCatalogueTones
import designsystem.Spacing
import designsystem.TvType
import designsystem.tvDp
import model.Kind
import model.MediaSet
import ui.common.catalog.TitlePreloadUi
import ui.common.catalog.heldInFull
import ui.tv.TvFocus
import ui.tv.TvTextRow
import ui.tv.rememberStableRequester

/**
 * One of [TvCollectionRows]' rows — a folder's heading, or an episode or
 * lesson to play — shared with a show's own Episodes tab, which lists one
 * season the same way. [focus] is the requester a caller lands the remote
 * on, for the one row that takes it. [preloadOf] is asked inside the row's
 * own lazy item, so only the rows on screen collect a preload state.
 */
@Composable
internal fun TvCollectionRow(
    row: CollectionRow,
    marks: WatchMarks,
    heldIds: Set<String>,
    onPlay: (setId: String) -> Unit,
    focus: FocusRequester?,
    modifier: Modifier = Modifier,
    preloadOf: (@Composable (MediaSet) -> TitlePreloadUi?)? = null,
) {
    when (row) {
        is CollectionRow.Heading ->
            TvSectionHeading(row.title, modifier.padding(start = indentOf(row.depth), bottom = SeasonHeadBelow), if (row.depth == 0) TopSeasonHeading else SeasonHeading)
        is CollectionRow.Item ->
            ItemRow(
                row = row,
                progress = watchedFractionOf(marks.positions[row.set.setId]),
                watched = row.set.setId in marks.watchedIds,
                held = row.set.setId in heldIds,
                onPlay = onPlay,
                focus = focus,
                modifier = modifier,
                preload = if (row.set.kind == Kind.DOCUMENT) null else preloadOf?.invoke(row.set),
            )
    }
}

/**
 * One lesson or episode to play, or one document, as the web's `.row`
 * (`catalog.css:76`): at least 56 tall, 16 above and below its words, a
 * hairline under it and none between it and the next. Its words start on
 * the page's edge rather than `.row`'s 8 in: a focus search reads anything
 * starting further left as lying to the left, and Left from an episode then
 * went to the season picker above instead of the rail — the tick before the
 * title so a column of them reads at a glance, and a progress rule along
 * its foot, and the offline badge under that for an episode or lesson this
 * device holds.
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
    preload: TitlePreloadUi?,
) {
    val text = "${row.number}. ${if (watched) "✓ " else ""}${row.set.title}"
    Column(modifier = modifier.fillMaxWidth().padding(start = indentOf(row.depth))) {
        Column(modifier = Modifier.fillMaxWidth().heightIn(min = RowMinHeight).padding(vertical = RowPaddingVertical)) {
            if (row.set.kind == Kind.DOCUMENT) {
                DocumentRow(text, focus)
            } else {
                // The title keeps one chain whether or not a control sits beside it: the remote may be on it as one arrives.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TvTextRow(text = text, onClick = { onPlay(row.set.setId) }, modifier = Modifier.weight(1f), focusRequester = focus)
                    preload?.let { TvEpisodePreload(it, row.set.totalBytes, Modifier.padding(start = Spacing.medium)) }
                }
                TvItemMarks(progress, heldInFull(held, preload, row.set.totalBytes))
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(LocalCatalogueTones.current.ruleSoft))
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
                .focusRequester(rememberStableRequester(focus))
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .semantics(mergeDescendants = true) { disabled() },
    ) {
        Text(
            text = text,
            style = TvFocus.textStyle(TvType.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurfaceVariant), focused),
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

/** `.row`'s `min-height` and the top and bottom of its `padding: 16px 8px` (`catalog.css:76`). */
private val RowMinHeight = 56.tvDp
private val RowPaddingVertical = 16.tvDp

/**
 * Space over a heading in a list of rows that sit flush: `.season`'s 32
 * (`catalog.css:70`), 24 for a nested one (`:74`), less the heading's own
 * small top — none over a list's first row, where the gap above the list
 * already stands, as the web's margins collapse into it.
 */
internal fun rowTopOf(
    row: CollectionRow,
    index: Int,
): Dp = if (row is CollectionRow.Heading && index > 0) (if (row.depth == 0) 32.tvDp else 24.tvDp) - Spacing.small else 0.dp

private const val IndentStep = 24
