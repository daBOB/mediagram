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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import catalog.Division
import catalog.Entry
import catalog.seasonPlatesOf
import designsystem.Spacing
import model.Kind
import model.MediaSet
import uniffi.mediagram_core.ShowInfo

/**
 * What is inside one show or course: its seasons or chapters, and the
 * episodes or lessons under them.
 *
 * A show with more than one season is a wall of season plates instead —
 * [SeasonWall] — because a course drills into chapters and a show into
 * seasons, and only the second has artwork of its own to put on a plate.
 * Everything else — a course, and a show with just one season — falls
 * through to the flat, indented list below, the same list a season screen
 * shows for the one season it was opened from ([SeasonScreen]).
 *
 * The nesting is kept rather than flattened. A course runs from one folder
 * deep to four, and flattening it gives a row of headings that each repeat
 * their parents; a viewer reads the indent instead.
 *
 * [info] describes the show or the course itself, not an episode of it —
 * the whole series shares one row in the index, which is why it can be
 * asked for with the key every episode under it carries. A course has
 * neither a provider entry nor artwork, so the block that would describe it
 * is left out entirely and the screen is the name and the tree, as it has
 * always been. An empty block would claim the library looked and found
 * nothing, when the truth is that nobody recorded it.
 */
@Composable
fun CollectionScreen(
    collection: Entry.Collection,
    info: ShowInfo?,
    posterPath: suspend (key: String) -> String?,
    onOpenTitle: (setId: String) -> Unit,
    onOpenSeason: (Division) -> Unit,
) {
    val seasons = remember(collection) { seasonPlatesOf(collection) }
    if (seasons != null) {
        SeasonWall(collection, info, seasons, posterPath, onOpenSeason)
        return
    }

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
        if (info != null || collection.posterPath != null) {
            item(key = "header") {
                TitleHeader(
                    posterPath = collection.posterPath,
                    title = collection.name,
                    // A show is not a file: it has no one year and no one
                    // runtime, and the seasons below already say how much
                    // of it there is.
                    facts = null,
                    info = info,
                    modifier = Modifier.padding(bottom = Spacing.medium),
                )
            }
        }
        items(rows, onOpenTitle)
    }
}

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

            is Row.Item -> ItemRow(row, onOpenTitle)
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
 */
@Composable
private fun ItemRow(row: Row.Item, onOpenTitle: (setId: String) -> Unit) {
    val document = row.set.kind == Kind.DOCUMENT
    Column(modifier = Modifier.padding(start = indentOf(row.depth))) {
        Text(
            text = "${row.position}. ${row.set.title}",
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
                .padding(vertical = Spacing.small),
        )
        if (document) {
            Text(
                text = DOCUMENT_REASON,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = Spacing.small),
            )
        }
        HorizontalDivider()
    }
}

/** Said under a document's own name, the way a disabled menu item says why. */
private const val DOCUMENT_REASON = "Document — the phone cannot open one yet"

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
