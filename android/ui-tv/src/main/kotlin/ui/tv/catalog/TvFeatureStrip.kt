package ui.tv.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.Feature
import catalog.FeatureKind
import catalog.initialsOf
import coil3.compose.AsyncImage
import designsystem.Spacing
import designsystem.TvTypeScale
import java.io.File
import model.Kind
import ui.tv.TvFocus

/** A feature card's own art, landscape rather than [TvPlate]'s poster crop — the backdrop it is actually cut from. */
private const val FeatureArtRatio = 16f / 9f

/** The label naming the rule that chose the title — the phone's own `labelFor`, ported verbatim. */
private fun labelFor(kind: FeatureKind): String =
    when (kind) {
        FeatureKind.EDITOR -> "Editor's choice"
        FeatureKind.STAFF -> "Staff pick"
        FeatureKind.TRENDING -> "Trending on TMDB"
        FeatureKind.NEW -> "New in the library"
    }

/**
 * The three feature cards under the cover story — the television twin of
 * the phone's `FeatureStrip`, over the same [Feature] picks.
 *
 * Landscape, at a backdrop's own proportions, rather than [TvPlate]'s
 * poster crop: a feature is picked for the same backdrop the cover story
 * itself uses, and cropping it to a poster's 2:3 both hides most of the
 * image and — three of them stacked under a 21:9 cover — pushes the row
 * below off a 540dp television screen. [TvPlate] is a poster's own shape by
 * design (every wall on this surface shares that one shape); a feature
 * card earns its own, smaller shape instead of stretching that one.
 *
 * Every unpinned feature is a film (`MagazineHome`'s own rule), so
 * [onOpenTitle] is the only route a card ever needs.
 */
@Composable
internal fun TvFeatureStrip(
    features: List<Feature>,
    onOpenTitle: (String) -> Unit,
    focusRequester: FocusRequester? = null,
) {
    if (features.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = Spacing.large),
        horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
    ) {
        features.forEachIndexed { index, feature ->
            val set = feature.set
            val series = set.kind == Kind.EPISODE && set.show != null
            val title = if (series) requireNotNull(set.show) else set.title
            val art = set.backdropPath ?: set.posterPath
            TvFeatureCard(
                title = title,
                artPath = art?.let(::File),
                meta = labelFor(feature.kind),
                caption = set.tagline?.ifEmpty { null },
                onOpen = { onOpenTitle(set.setId) },
                modifier =
                    Modifier
                        .weight(1f)
                        .let { m -> if (index == 0 && focusRequester != null) m.focusRequester(focusRequester) else m },
            )
        }
    }
}

@Composable
private fun TvFeatureCard(
    title: String,
    artPath: File?,
    meta: String,
    caption: String?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onOpen,
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = TvFocus.cardShape(),
        scale = TvFocus.cardScale(),
        border = TvFocus.cardBorder(),
        glow = TvFocus.cardGlow(),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(FeatureArtRatio).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                if (artPath != null) {
                    AsyncImage(model = artPath, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                } else {
                    Text(text = initialsOf(title), style = TvTypeScale.title, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Text(
                text = title,
                style = TvTypeScale.body,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.small, start = Spacing.small, end = Spacing.small),
            )
            Text(
                text = meta,
                style = TvTypeScale.body,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = Spacing.small),
            )
            caption?.let {
                Text(
                    text = it,
                    style = TvTypeScale.body,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = Spacing.small, vertical = Spacing.small),
                )
            }
        }
    }
}
