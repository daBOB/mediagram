package ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import catalog.Feature
import catalog.FeatureKind
import coil3.compose.AsyncImage
import model.Kind
import designsystem.Spacing
import java.io.File

/** The label naming the rule that chose the title — `home-features.js`'s `FEATURE_LABELS`, verbatim. */
private fun labelFor(kind: FeatureKind): String =
    when (kind) {
        FeatureKind.EDITOR -> "Editor’s choice"
        FeatureKind.STAFF -> "Staff pick"
        FeatureKind.TRENDING -> "Trending on TMDB"
        FeatureKind.NEW -> "New in the library"
    }

/**
 * The three features under the cover: each one title, its backdrop (or
 * poster, where there is no backdrop), a department label, and its tagline
 * as the standfirst — a Compose port of `home-features.js`.
 *
 * Every unpinned feature is a film (see `MagazineHome.kt`), so `onOpenTitle`
 * is the only route a card ever needs. A pin can be an episode — the web
 * opens its show's page then (`featureHref`) — but the title screen this
 * opens for one plays that exact episode, which is not a wrong place to
 * land from a household's own pin, just a narrower one than the web takes;
 * closing that gap is future work, not a silent drop of the pin itself.
 */
@Composable
internal fun FeatureStrip(
    features: List<Feature>,
    onOpenTitle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (features.isEmpty()) return
    Row(modifier = modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        for (feature in features) {
            FeatureCard(
                feature = feature,
                modifier = Modifier.weight(1f),
                onClick = { onOpenTitle(feature.set.setId) },
            )
        }
    }
}

@Composable
private fun FeatureCard(
    feature: Feature,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val set = feature.set
    val series = set.kind == Kind.EPISODE && set.show != null
    val title = if (series) requireNotNull(set.show) else set.title
    val art = set.backdropPath ?: set.posterPath
    Column(
        modifier =
            modifier
                .clickable(role = Role.Button, onClick = onClick),
    ) {
        Box(modifier = Modifier.fillMaxWidth().aspectRatio(4f / 3f)) {
            if (art != null) {
                AsyncImage(
                    model = File(art),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize(),
                )
            } else {
                Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant))
            }
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))),
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(Spacing.small)) {
                Text(
                    text = labelFor(feature.kind),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        val deck = set.tagline?.ifEmpty { null } ?: set.genres.take(3).joinToString(", ").ifEmpty { null }
        if (deck != null) {
            Text(
                text = deck,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.extraSmall),
            )
        }
        val year = set.year?.takeIf { !series && it > 0 }?.toString()
        val meta = listOfNotNull(year, ratingLabel(set.rating)).joinToString(" · ").ifEmpty { null }
        if (meta != null) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
