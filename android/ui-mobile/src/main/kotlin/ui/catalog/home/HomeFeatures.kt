package ui.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import catalog.Feature
import catalog.label
import coil3.compose.AsyncImage
import designsystem.Eyebrow
import model.Kind
import java.io.File

/** The features block's own outer bounds, for a test to check it lands under the cover, not behind it. */
internal const val HOME_FEATURES_TEST_TAG = "home-features"

/**
 * The features under the cover: each one title over its own backdrop, a
 * department label above and a rule-and-deck below — a Compose port of
 * `home-features.js`. Replaces `FeatureStrip`.
 *
 * Three equal columns above [WideBreakpoint]; two below it with the first
 * spanning both (the tablet's own shape, `home.css:343-345`); one column
 * compact. [width] is the window's own width, for the same breakpoints and
 * fluid sizes [HomeCover] reads.
 */
@Composable
internal fun HomeFeatures(
    features: List<Feature>,
    width: Dp,
    onOpenTitle: (String) -> Unit,
) {
    if (features.isEmpty()) return
    // A minimum, not an exact height, on every card below — the web's own
    // `min-height` (`home.css:142`, `345`): a card is `max(min, its own
    // text)` tall, never cropped to the min when a deck runs to three
    // lines. `FeatureCard`'s own backdrop and scrim read `matchParentSize()`
    // rather than `fillMaxSize()` so they never *drive* that size themselves
    // — a `fillMaxSize()` sibling of an unbounded `Column` is what once let
    // a card balloon to fill all the height its own `Column` had, leaving
    // nothing for the row below it to measure into; `matchParentSize()`
    // only ever matches whatever the card's own text already resolved it
    // to.
    val cardHeight = fluid(220f, 0.16f, 270f, width.value).dp
    when {
        width <= CompactBreakpoint ->
            Column(
                modifier = Modifier.testTag(HOME_FEATURES_TEST_TAG).fillMaxWidth().padding(horizontal = gutterFor(width)),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for ((index, feature) in features.withIndex()) {
                    FeatureCard(feature, index, width, Modifier.fillMaxWidth().heightIn(min = 280.dp), onOpenTitle)
                }
            }

        width <= WideBreakpoint -> {
            // The web's own CSS reads `min-height: 360px` on this first card
            // at this breakpoint, but the grid it sits in never actually
            // rows it out that tall — the reference shot's own wide card
            // sits at the same height as the pair below, not 360px's worth
            // taller. Matched to the shot, not the declared min.
            Column(
                modifier = Modifier.testTag(HOME_FEATURES_TEST_TAG).fillMaxWidth().padding(horizontal = gutterFor(width)),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                features.getOrNull(0)?.let { FeatureCard(it, 0, width, Modifier.fillMaxWidth().heightIn(min = cardHeight), onOpenTitle) }
                if (features.size > 1) {
                    // `IntrinsicSize.Max` on the row, `fillMaxHeight()` on
                    // each card: the web's grid stretches the pair to match
                    // each other, which a bare `weight(1f)` never does on
                    // its own — a 3-line title plus a 3-line deck otherwise
                    // sits taller than a plain one beside it. `heightIn(min
                    // = cardHeight)` still runs first, so a short pair still
                    // floors at the same height every other card does.
                    Row(
                        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Max),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        for (index in 1 until features.size) {
                            FeatureCard(features[index], index, width, Modifier.weight(1f).heightIn(min = cardHeight).fillMaxHeight(), onOpenTitle)
                        }
                    }
                }
            }
        }

        else ->
            Row(
                modifier = Modifier.testTag(HOME_FEATURES_TEST_TAG).fillMaxWidth().padding(horizontal = gutterFor(width)),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                for ((index, feature) in features.withIndex()) {
                    FeatureCard(feature, index, width, Modifier.weight(1f).heightIn(min = cardHeight), onOpenTitle)
                }
            }
    }
}

@Composable
private fun FeatureCard(
    feature: Feature,
    index: Int,
    width: Dp,
    modifier: Modifier,
    onOpenTitle: (String) -> Unit,
) {
    val set = feature.set
    val series = set.kind == Kind.EPISODE && set.show != null
    val title = if (series) requireNotNull(set.show) else set.title
    val art = set.backdropPath ?: set.posterPath
    val posterOnly = set.backdropPath == null
    // The second card keeps its own case and runs a size larger — `home.css:180-181`.
    val secondCard = index == 1

    Box(
        modifier =
            modifier
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF141416))
                .clickable(role = Role.Button, onClick = { onOpenTitle(set.setId) }),
    ) {
        if (art != null) {
            AsyncImage(
                model = File(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                // A poster fallback crops toward its own top third rather
                // than a backdrop's own centre-right focus — `.poster-crop`
                // (`home.css:159`).
                alignment = if (posterOnly) Alignment.TopCenter else Alignment.CenterEnd,
                // `matchParentSize()`, not `fillMaxSize()`: this never
                // *asks* for height (that would fight the text below for
                // it, the way it once did) — it only ever matches whatever
                // the card's own text already resolved the box to.
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

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .widthIn(max = 416.dp)
                    .padding(vertical = 36.dp, horizontal = fluid(24f, 0.026f, 40f, width.value).dp),
        ) {
            Text(text = feature.kind.label.uppercase(), style = Eyebrow, color = OnImage2, modifier = Modifier.padding(bottom = 18.dp))
            val titleSize = if (secondCard) fluid(32f, 0.03f, 49.6f, width.value) else fluid(28.8f, 0.028f, 46.4f, width.value)
            Text(
                text = if (secondCard) title else title.uppercase(),
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = titleSize.sp,
                    lineHeight = 0.98.em,
                    letterSpacing = (-0.025).em,
                ),
                color = OnImage,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Box(modifier = Modifier.padding(top = 20.dp, bottom = 16.dp).width(32.dp).height(1.dp).background(Color(0x8CF6F2EA)))
            val deck = set.tagline?.ifEmpty { null } ?: set.genres.take(3).joinToString(", ").ifEmpty { null }
            if (deck != null) {
                Text(
                    text = deck,
                    style = MaterialTheme.typography.bodyLarge.copy(fontSize = 16.sp, lineHeight = 1.4.em),
                    color = OnImage2,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = 384.dp),
                )
            }
        }
    }
}
