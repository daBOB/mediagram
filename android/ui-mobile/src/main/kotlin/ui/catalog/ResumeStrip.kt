package ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import catalog.SetCard
import coil3.compose.AsyncImage
import designsystem.Spacing
import model.Kind
import model.episodeLabel
import java.io.File

private val CARD_WIDTH = 220.dp

/**
 * Continue watching, set quietly: landscape cards in one scrolling row, a
 * progress bar over the picture rather than a caption naming it, each with
 * where the viewer stopped — a Compose port of `home-resume.js`.
 *
 * [cards] is already Continue and Next up merged into the one strip the web
 * draws (see `MagazineHome.kt`'s `magazineHomeOf`), rather than two rows.
 */
@Composable
internal fun ResumeStrip(
    cards: List<SetCard>,
    onOpenTitle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (cards.isEmpty()) return
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.medium),
    ) {
        items(items = cards, key = { it.set.setId }) { card ->
            ResumeCard(card = card, onClick = { onOpenTitle(card.set.setId) })
        }
    }
}

@Composable
private fun ResumeCard(
    card: SetCard,
    onClick: () -> Unit,
) {
    val set = card.set
    val episode = set.kind != Kind.MOVIE && set.show != null
    val name = if (episode) requireNotNull(set.show) else set.title
    val art = set.backdropPath ?: set.posterPath

    Column(
        modifier =
            Modifier
                .width(CARD_WIDTH)
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(modifier = Modifier.width(CARD_WIDTH).aspectRatio(16f / 9f).clip(RoundedCornerShape(4.dp))) {
            Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant)) {
                if (art != null) {
                    AsyncImage(
                        model = File(art),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                } else {
                    Text(
                        text = initialsOf(name),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            val progress = card.progress
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress.coerceIn(0f, 1f) },
                    modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
                )
            }
        }
        Text(
            text = name,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.small),
        )
        val sub =
            if (episode) {
                listOfNotNull(episodeLabel(set).ifEmpty { null }, set.title).joinToString(" · ")
            } else {
                factsLine(set.year, set.durationSecs)
            }
        if (!sub.isNullOrEmpty()) {
            Text(
                text = sub,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (card.caption.isNotEmpty()) {
            Text(
                text = card.caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
