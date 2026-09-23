package ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import designsystem.Spacing

/**
 * The masthead the web player has: the three catalog shelves, then the
 * four that come from what has been watched rather than from the catalog —
 * `index.html`'s own order, Home, Movies, Series, Tutorials, Continue,
 * Watchlist, Collections, Kids.
 *
 * Scrollable rather than fixed-width: eight labels do not fit a phone's
 * width the way three did, and a `PrimaryScrollableTabRow` is the platform's
 * own answer to a masthead too wide for its screen — a tablet's own width
 * shows every tab at once regardless. [firstKeptIndex] draws a thin rule
 * before the first kept tab, the web's `class="kept"` said in this
 * platform's own vocabulary: a `TabRow` lays its tabs in one row with
 * nothing between them, so the break is drawn on the tab itself rather than
 * inserted as a sibling.
 *
 * Text and no icons, because there are no icons in this world and a drawn
 * one would be inventing a mark for a shelf that already has a name.
 */
@Composable
internal fun ShelfTabs(
    titles: List<String>,
    selected: Int,
    firstKeptIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ruleColor = MaterialTheme.colorScheme.outlineVariant
    PrimaryScrollableTabRow(
        selectedTabIndex = selected,
        modifier = modifier.fillMaxWidth(),
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
                modifier = Modifier
                    .padding(vertical = Spacing.extraSmall)
                    .let { if (index == firstKeptIndex) it.leadingRule(ruleColor) else it },
                selectedContentColor = MaterialTheme.colorScheme.onSurface,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** A hairline down the tab's leading edge — see [ShelfTabs]'s own note on why it is drawn here rather than between tabs. */
private fun Modifier.leadingRule(color: Color): Modifier = drawBehind {
    drawLine(
        color = color,
        start = Offset(0f, size.height * 0.25f),
        end = Offset(0f, size.height * 0.75f),
        strokeWidth = 1.dp.toPx(),
    )
}
