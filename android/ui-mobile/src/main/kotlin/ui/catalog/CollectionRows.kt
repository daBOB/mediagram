package ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import catalog.Division
import designsystem.Spacing
import model.Kind
import model.MediaSet
import model.Progress

/**
 * The flat, indented row rendering [CollectionScreen] and [SeasonScreen]
 * both draw from — split out once the screen around it grew past what one
 * file should carry alongside the layout that places these rows.
 */

/**
 * One line of the screen: a heading for a division, or a set under it.
 *
 * Internal rather than private: [SeasonScreen] renders the same rows for
 * the one division a season plate was opened from, and a set of episodes
 * is not something worth two rendering paths.
 */
internal sealed interface Row {
    val depth: Int

    data class Heading(override val depth: Int, val title: String) : Row
    data class Item(override val depth: Int, val set: MediaSet, val position: Int) : Row
}

/**
 * The divisions and their sets, in reading order, each with how deep it
 * sits.
 *
 * A division holding nothing but folders still gets its heading: it is how
 * the course was built, and dropping it would join two levels that are not
 * the same level.
 */
internal fun rowsOf(divisions: List<Division>, depth: Int = 0): List<Row> =
    divisions.flatMap { division ->
        buildList {
            add(Row.Heading(depth, division.title))
            division.items.forEachIndexed { index, set -> add(Row.Item(depth, set, index + 1)) }
            addAll(rowsOf(division.children, depth + 1))
        }
    }

internal fun LazyListScope.items(
    rows: List<Row>,
    positions: Map<String, Progress>,
    watchedIds: Set<String>,
    heldIds: Set<String>,
    onOpenTitle: (setId: String) -> Unit,
) {
    items(
        count = rows.size,
        key = { index -> keyOf(rows[index], index) },
    ) { index ->
        when (val row = rows[index]) {
            is Row.Heading -> Text(
                text = row.title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = indentOf(row.depth), top = Spacing.medium),
            )

            is Row.Item -> ItemRow(row, positions[row.set.setId], row.set.setId in watchedIds, row.set.setId in heldIds, onOpenTitle)
        }
    }
}

/**
 * One lesson or episode to open, or one document.
 *
 * A document is shown and not opened. Nothing here can display a handout —
 * the player would be handed a PDF — so the row says what it is rather than
 * offering a tap that could only fail, which is how a disabled menu item
 * already behaves. Leaving it out was the older behaviour and the worse
 * one: a workbook that is simply absent reads as an upload that failed, and
 * a folder holding nothing else disappears with it.
 *
 * [progress] and [watched] are `null`/`false` for a document — it is never
 * played, so it carries neither. Ported from `course-view.js`'s
 * `lessonRow`: a tick before the title rather than after it, so a column of
 * them down a season reads at a glance, and a progress rule along the foot
 * of the row, the same rule a plate draws along its own. [held] adds the
 * "offline" badge under the title, as `lessonRow` does: an episode or a
 * lesson is the thing most likely to be held in full, and a season list is
 * where a viewer looks for what the preload already took.
 */
@Composable
private fun ItemRow(row: Row.Item, progress: Progress?, watched: Boolean, held: Boolean, onOpenTitle: (setId: String) -> Unit) {
    val document = row.set.kind == Kind.DOCUMENT
    Column(modifier = Modifier.padding(start = indentOf(row.depth))) {
        Text(
            text = "${row.position}. ${if (watched) "✓ " else ""}${row.set.title}",
            style = MaterialTheme.typography.bodyLarge,
            color = if (document) MaterialTheme.colorScheme.onSurfaceVariant else Color.Unspecified,
            modifier = Modifier
                .fillMaxWidth()
                // The whole line is the target, and the role is what tells a
                // screen reader it is one. A document is not a target at all.
                .then(
                    if (document) {
                        Modifier
                    } else {
                        Modifier.clickable(role = Role.Button) { onOpenTitle(row.set.setId) }
                    },
                )
                .padding(top = Spacing.small, bottom = if (document) 0.dp else Spacing.small),
        )
        if (document) {
            Text(
                text = DOCUMENT_REASON,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.small),
            )
        }
        if (held) OfflineBadge(modifier = Modifier.padding(bottom = Spacing.small))
        watchedFractionOf(progress)?.let { fraction ->
            LinearProgressIndicator(
                progress = { fraction },
                modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.small),
            )
        }
        HorizontalDivider()
    }
}

/**
 * Said under a document's own name, the way a disabled menu item says why.
 * Internal rather than private: [SearchResultRow] says the same thing when
 * a search hit names a document, for the same reason.
 */
internal const val DOCUMENT_REASON = "Document — the phone cannot open one yet"

/**
 * A set id is unique and a heading is not — two courses can both have a
 * folder called "Grundlagen" — so a heading is keyed by where it sits.
 */
private fun keyOf(row: Row, index: Int): String = when (row) {
    is Row.Heading -> "heading-$index-${row.title}"
    is Row.Item -> row.set.setId
}

private fun indentOf(depth: Int) = (depth * INDENT_STEP).dp

private const val INDENT_STEP = 16
