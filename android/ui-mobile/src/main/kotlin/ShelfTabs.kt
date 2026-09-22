package ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import catalog.Shelf
import designsystem.Spacing
import androidx.compose.foundation.layout.padding

/**
 * Which shelf the wall is showing, as the masthead the web player has.
 *
 * One shelf at a time, not all three stacked. A wall puts everything a
 * shelf holds on the page, and this library's film shelf alone is three
 * hundred plates deep — so with the shelves stacked, the courses would sit
 * fifty screens below the fold and nothing would say they were there. The
 * web player answers this with separate routes and a masthead; tabs are the
 * same answer in the platform's own vocabulary.
 *
 * Text and no icons, because there are no icons in this world and a drawn
 * one would be inventing a mark for a shelf that already has a name.
 */
@Composable
internal fun ShelfTabs(
    shelves: List<Shelf>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    PrimaryTabRow(
        selectedTabIndex = selected,
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        shelves.forEachIndexed { index, shelf ->
            Tab(
                selected = index == selected,
                onClick = { onSelect(index) },
                text = {
                    Text(
                        text = shelf.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                // The label is the whole target, and a tab that hugs its
                // text is under Material's 48dp on a short word like
                // "Series".
                modifier = Modifier.padding(vertical = Spacing.extraSmall),
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
