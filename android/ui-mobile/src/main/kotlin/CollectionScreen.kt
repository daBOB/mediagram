package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import catalog.Division
import catalog.Entry
import designsystem.Spacing
import model.MediaSet

/**
 * What is inside one show or course: its seasons or chapters, and the
 * episodes or lessons under them.
 *
 * The nesting is kept rather than flattened. A course runs from one folder
 * deep to four, and flattening it gives a row of headings that each repeat
 * their parents; a viewer reads the indent instead.
 */
@Composable
fun CollectionScreen(collection: Entry.Collection, onPlay: (setId: String) -> Unit) {
    // Flattened once per collection, not on every recomposition: the depth
    // becomes an indent here because a lazy list cannot nest, and a viewer
    // still has to see which folder holds what.
    val rows = remember(collection) { rowsOf(collection.divisions) }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.large),
        verticalArrangement = Arrangement.spacedBy(Spacing.small),
    ) {
        item {
            Text(
                text = collection.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(bottom = Spacing.medium),
            )
        }
        items(rows, onPlay)
    }
}

/** One line of the screen: a heading for a division, or a set to play. */
private sealed interface Row {
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
private fun rowsOf(divisions: List<Division>, depth: Int = 0): List<Row> =
    divisions.flatMap { division ->
        buildList {
            add(Row.Heading(depth, division.title))
            division.items.forEachIndexed { index, set -> add(Row.Item(depth, set, index + 1)) }
            addAll(rowsOf(division.children, depth + 1))
        }
    }

private fun LazyListScope.items(
    rows: List<Row>,
    onPlay: (setId: String) -> Unit,
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

            is Row.Item -> Column(modifier = Modifier.padding(start = indentOf(row.depth))) {
                Text(
                    text = "${row.position}. ${row.set.title}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        // The whole line is the target, and the role is what
                        // tells a screen reader it is one.
                        .clickable(role = Role.Button) { onPlay(row.set.setId) }
                        .padding(vertical = Spacing.small),
                )
                HorizontalDivider()
            }
        }
    }
}

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
