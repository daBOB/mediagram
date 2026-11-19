package ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import catalog.Division
import catalog.spelledCountOf
import catalog.walk
import designsystem.Backdrop
import designsystem.LocalBackdrop
import designsystem.Radius
import designsystem.Spacing

/**
 * A show's seasons as a row of cards over its episode list — the web's
 * `season-cards.js` and the television's `TvSeasonPicker`, in place of the
 * drop-down that stood here: every season in sight, each its own cover where
 * the library holds one (an episode's `seasonPosterPath`), else [showPoster]
 * with the season's number set over it so the cards still tell apart. The
 * watched share is the plate's rule; under it the season, how many episodes
 * it holds and how many of them are watched; the season showing has a rule
 * under it, as the showing tab does. The row opens on [shown], so a show
 * reopened on Season 9 does not start with it out of sight.
 */
@Composable
internal fun SeasonCards(
    divisions: List<Division>,
    shown: Division,
    showPoster: String?,
    watchedIds: Set<String>,
    onSelect: (title: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val row = rememberLazyListState(initialFirstVisibleItemIndex = divisions.indexOf(shown).coerceAtLeast(0))
    LazyRow(
        state = row,
        modifier = modifier.semantics { contentDescription = "Seasons" },
        contentPadding = PaddingValues(horizontal = Spacing.large, vertical = Spacing.small),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        items(divisions, key = { it.title }) { division ->
            SeasonCard(division, showPoster, watchedIds, showing = division == shown, onClick = { onSelect(division.title) })
        }
    }
}

@Composable
private fun SeasonCard(division: Division, showPoster: String?, watchedIds: Set<String>, showing: Boolean, onClick: () -> Unit) {
    val sets = remember(division) { division.walk().flatMap { it.items.asSequence() }.toList() }
    val watched = sets.count { it.setId in watchedIds }
    val cover = remember(division) { sets.firstNotNullOfOrNull { it.seasonPosterPath } }
    val number = division.season?.toString() ?: division.title
    val shape = if (LocalBackdrop.current == Backdrop.AMBIENT) RoundedCornerShape(Radius.card) else RectangleShape
    Column(
        modifier = Modifier
            .width(SeasonCardWidth)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { selected = showing },
    ) {
        Box(Modifier.clip(shape)) {
            PosterArt(
                posterPath = cover ?: showPoster,
                // With no art at all the initials stand in, and they are the number.
                title = number,
                progress = if (watched == 0 || watched == sets.size) null else watched.toFloat() / sets.size,
                watched = sets.isNotEmpty() && watched == sets.size,
            )
            // The show's poster standing in for every season: the number over it tells them apart.
            if (cover == null && showPoster != null) {
                Box(Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f))))
                Text(
                    text = number,
                    style = MaterialTheme.typography.displaySmall,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.small),
                )
            }
        }
        Text(division.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.padding(top = Spacing.small))
        Text(spelledCountOf(sets.size, "episode"), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (watched > 0) {
            Text("$watched of ${sets.size} watched", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // The season showing, marked as the showing tab is: a rule under it.
        Box(
            Modifier
                .padding(top = Spacing.small)
                .fillMaxWidth()
                .height(2.dp)
                .background(if (showing) MaterialTheme.colorScheme.onBackground else Color.Transparent),
        )
    }
}

/** A poster plate's width on the department grids, so the row reads as the same kind of card. */
private val SeasonCardWidth = 132.dp
