package ui.tv.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.em
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import catalog.Feature
import catalog.label
import coil3.compose.AsyncImage
import designsystem.TvType
import designsystem.TvType.Eyebrow
import designsystem.tvDp
import designsystem.tvSp
import java.io.File
import model.Kind
import ui.tv.TvFocus
import ui.tv.catalog.DisplaySemiBold
import ui.tv.rememberStableRequester

/** `.feature`'s `min-height: clamp(220px, 16vw, 270px)` at the television's 1280px (`home.css:142`). */
private val FeatureMinHeight = 220.tvDp

/**
 * The three feature cards under the cover story, side by side in equal
 * columns — the television twin of the phone's `HomeFeatures`, at the web's
 * own three-column grid (`home.css:131-134`): the TV reads the web at
 * 1280px, above the 1180px breakpoint where the web stacks the first card
 * over the other two. Every unpinned feature is a film
 * ([catalog.MagazineHome]'s own rule), so [onOpenTitle] is the only route a
 * card ever needs.
 *
 * `IntrinsicSize.Max` on the row and `fillMaxHeight()` on each card: the
 * web's grid stretches a row's cards to its tallest, a three-line title
 * beside a one-line one included, which a bare `weight(1f)` never does.
 */
@Composable
internal fun TvHomeFeatures(
    features: List<Feature>,
    onOpenTitle: (String) -> Unit,
    focusAt: Int? = null,
    focus: FocusRequester? = null,
) {
    if (features.isEmpty()) return
    Row(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max), horizontalArrangement = Arrangement.spacedBy(FeatureGap)) {
        for ((index, feature) in features.withIndex()) {
            key(feature.set.setId) {
                TvFeatureCard(
                    feature = feature,
                    index = index,
                    onOpenTitle = onOpenTitle,
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = FeatureMinHeight)
                            .fillMaxHeight()
                            .focusRequester(rememberStableRequester(focus?.takeIf { index == focusAt })),
                )
            }
        }
        // `repeat(3, …)`: fewer features leave the web's own columns empty rather than widen.
        repeat(3 - features.size) { Spacer(Modifier.weight(1f)) }
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
    // web's own rule (`home.css:179-180`).
    val secondCard = index == 1

    Card(
        onClick = { onOpenTitle(set.setId) },
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = TvFocus.cardShape(TvFocus.featureCardShape),
        scale = TvFocus.cardScale(),
        border = TvFocus.cardBorder(TvFocus.featureCardShape),
        glow = TvFocus.cardGlow(),
        colors = CardDefaults.colors(containerColor = Color(0xFF141416)),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (art != null) {
                // `object-position: 60% 30%`, a poster's own `50% 18%` (`home.css:156`, `159`).
                AsyncImage(
                    model = File(art),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = if (set.backdropPath != null) BiasAlignment(0.2f, -0.4f) else BiasAlignment(0f, -0.64f),
                    modifier = Modifier.matchParentSize(),
                )
            }
            Box(
                modifier =
                    Modifier.matchParentSize().background(
                        Brush.horizontalGradient(0f to Color(0xE0080809), 0.48f to Color(0x85080809), 1f to Color(0x14080809)),
                    ),
            )
            Box(modifier = Modifier.matchParentSize().background(Brush.verticalGradient(0.45f to Color(0x00080809), 1f to Color(0xB3080809))))

            // `.feature-copy`: 26rem wide, padded 36 by `clamp(24px, 2.6vw, 40px)` at 1280px; its eyebrow 18 above the title (`home.css:169-170`).
            Column(modifier = Modifier.align(Alignment.BottomStart).widthIn(max = 416.tvDp).padding(horizontal = 33.28.tvDp, vertical = 36.tvDp)) {
                Text(
                    text = feature.kind.label.uppercase(),
                    style = Eyebrow,
                    color = OnImage2,
                    modifier = Modifier.padding(bottom = 18.tvDp),
                )
                // `clamp(1.8rem, 2.8vw, 2.9rem)/0.98`, the second `clamp(2rem, 3vw, 3.1rem)`, at 1280px (`home.css:172`, `180`).
                Text(
                    text = if (secondCard) title else title.uppercase(),
                    style =
                        DisplaySemiBold.copy(
                            fontSize = if (secondCard) 38.4.tvSp else 35.84.tvSp,
                            lineHeight = 0.98.em,
                            letterSpacing = (-0.025).em,
                        ),
                    color = OnImage,
                )
                // `.feature-rule`: 32 by 1, 20 above and 16 below (`home.css:177`).
                Box(modifier = Modifier.padding(top = 20.tvDp, bottom = 16.tvDp).size(width = 32.tvDp, height = 1.tvDp).background(FeatureRule))
                val deck = set.tagline?.ifEmpty { null } ?: set.genres.take(3).joinToString(", ").ifEmpty { null }
                if (deck != null) {
                    Text(
                        text = deck,
                        style = TvType.bodyLarge.copy(fontSize = 16.tvSp, lineHeight = 1.4.em),
                        color = OnImage2,
                    )
                }
            }
        }
    }
}

/** `.feature-rule`'s `rgba(246, 242, 234, 0.55)`. */
private val FeatureRule = Color(0x8CF6F2EA)

/** `.features`' gap (`home.css:134`). */
private val FeatureGap = 12.tvDp
