package ui.catalog

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import catalog.SetCard
import catalog.episodeLabel

/**
 * A card for a raw set — a film, an episode or a lesson — as Continue and
 * Next up show it, over [PosterCard]. [SetCard.caption] is already this
 * viewer's own place in it or "Next up"; [meta] is built here instead,
 * because it is a fact about the title rather than the viewer, the same
 * split `shelf-view.js`'s `setGrid` keeps between `meta` and `resume`.
 */
@Composable
internal fun SetPlate(
    card: SetCard,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val set = card.set
    PosterCard(
        posterPath = set.posterPath,
        title = set.title,
        meta = listOfNotNull(set.show, episodeLabel(set).ifEmpty { null }).joinToString(" · ").ifEmpty { null },
        caption = card.caption.ifEmpty { null },
        progress = card.progress,
        watched = card.watched,
        modifier = modifier,
        onClick = onClick,
    )
}
