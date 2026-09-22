package ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import designsystem.Spacing

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
    titles: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Held to a readable measure instead of filling the window. Three
    // labels spread across a tablet's twelve hundred points put "Movies"
    // and "Tutorials" at opposite edges of the glass, which reads as three
    // unrelated buttons rather than as one masthead.
    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Masthead(titles, selected, onSelect)
    }
}

@Composable
private fun Masthead(titles: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    PrimaryTabRow(
        selectedTabIndex = selected,
        modifier = Modifier.widthIn(max = MASTHEAD_MAX_WIDTH),
        // Transparent, so the masthead is type on the page rather than a
        // band laid over it. Given its own colour it becomes a filled
        // container floating between the bar and the wall, which is the one
        // thing this world does not do.
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        titles.forEachIndexed { index, name ->
            Tab(
                selected = index == selected,
                onClick = { onSelect(index) },
                text = {
                    Text(
                        text = name,
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

/** As wide as three names need, and no wider. */
private val MASTHEAD_MAX_WIDTH = 560.dp
