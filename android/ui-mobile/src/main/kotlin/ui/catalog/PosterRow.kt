package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import designsystem.Spacing

private val CARD_WIDTH = 130.dp

/** One title a Similar row offers, already resolved to what a [PosterCard] needs. */
internal data class PosterRowItem(
    val key: String,
    val posterPath: String?,
    val title: String,
    val caption: String?,
    val onClick: () -> Unit,
)

/**
 * A shelf of poster cards in one scrolling row — the "Similar" tab on both
 * the film and the series page, a Compose port of `shelf-view.js`'s own
 * strip mode as `film-page.js`/`series-page.js` call it. The empty message
 * is the web's own: nothing else in the library shares its genres.
 */
@Composable
internal fun PosterRow(items: List<PosterRowItem>) {
    if (items.isEmpty()) {
        Text(
            text = "Nothing else in the library shares its genres.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
        contentPadding = PaddingValues(vertical = Spacing.small),
    ) {
        items(items = items, key = PosterRowItem::key) { item ->
            PosterCard(
                posterPath = item.posterPath,
                title = item.title,
                caption = item.caption,
                modifier = Modifier.width(CARD_WIDTH),
                onClick = item.onClick,
            )
        }
    }
}
