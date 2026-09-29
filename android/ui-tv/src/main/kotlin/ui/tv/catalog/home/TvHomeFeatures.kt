package ui.tv.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import catalog.Feature
import catalog.label
import coil3.compose.AsyncImage
import designsystem.Eyebrow
import designsystem.Spacing
import designsystem.TvTypeScale
import java.io.File
import model.Kind
import ui.tv.TvFocus

/** The first card's own height, full width — the phone's own two-tier shape (`home.css:343-345`) at TV's one width. */
private val LeadCardHeight = 300.dp

/** The second and third cards, side by side under the lead — the phone's own pair. */
private val PairCardHeight = 220.dp

/**
 * The three feature cards under the cover story: one full width, two beside
 * each other under it — the television twin of the phone's `HomeFeatures`,
 * at TV's one width rather than the phone's own three breakpoints. Every
 * unpinned feature is a film ([catalog.MagazineHome]'s own rule), so
 * [onOpenTitle] is the only route a card ever needs.
 */
@Composable
internal fun TvHomeFeatures(
    features: List<Feature>,
    onOpenTitle: (String) -> Unit,
    focusAt: Int? = null,
    focus: FocusRequester? = null,
) {
    if (features.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.medium)) {
        features.getOrNull(0)?.let { feature ->
            TvFeatureCard(
                feature = feature,
                index = 0,
                onOpenTitle = onOpenTitle,
                modifier = Modifier.fillMaxWidth().height(LeadCardHeight).let { if (focusAt == 0 && focus != null) it.focusRequester(focus) else it },
            )
        }
        if (features.size > 1) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.medium)) {
                for (index in 1 until features.size) {
                    TvFeatureCard(
                        feature = features[index],
                        index = index,
                        onOpenTitle = onOpenTitle,
                        modifier =
                            Modifier
                                .weight(1f)
                                .height(PairCardHeight)
                                .let { if (index == focusAt && focus != null) it.focusRequester(focus) else it },
                    )
                }
            }
        }
    }
}

@Composable
private fun TvFeatureCard(
    feature: Feature,
    index: Int,
    onOpenTitle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val set = feature.set
    val series = set.kind == Kind.EPISODE && set.show != null
    val title = if (series) requireNotNull(set.show) else set.title
    val art = set.backdropPath ?: set.posterPath
    // The second card keeps its own case and runs a size larger — the
    // phone's own rule (`home.css:180-181`).
    val secondCard = index == 1

    Card(
        onClick = { onOpenTitle(set.setId) },
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = TvFocus.cardShape(TvFocus.FeatureCardShape),
        scale = TvFocus.cardScale(),
        border = TvFocus.cardBorder(TvFocus.FeatureCardShape),
        glow = TvFocus.cardGlow(),
        colors = CardDefaults.colors(containerColor = Color(0xFF141416)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (art != null) {
                AsyncImage(model = File(art), contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
            }
            Box(
                modifier =
                    Modifier.matchParentSize().background(
                        Brush.horizontalGradient(0f to Color(0xE0080809), 0.48f to Color(0x85080809), 1f to Color(0x14080809)),
                    ),
            )
            Box(modifier = Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color(0x00080809), 1f to Color(0xB3080809))))

            Column(modifier = Modifier.align(Alignment.BottomStart).widthIn(max = 480.dp).padding(horizontal = Spacing.large, vertical = Spacing.medium)) {
                Text(
                    text = feature.kind.label.uppercase(),
                    style = Eyebrow.copy(fontSize = TvTypeScale.eyebrow),
                    color = OnImage2,
                    modifier = Modifier.padding(bottom = Spacing.small),
                )
                Text(
                    text = if (secondCard) title else title.uppercase(),
                    style = TvTypeScale.title.copy(fontSize = if (secondCard) 26.sp else 24.sp),
                    color = OnImage,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val deck = set.tagline?.ifEmpty { null } ?: set.genres.take(3).joinToString(", ").ifEmpty { null }
                if (deck != null) {
                    Text(
                        text = deck,
                        style = TvTypeScale.body,
                        color = OnImage2,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = Spacing.small),
                    )
                }
            }
        }
    }
}
