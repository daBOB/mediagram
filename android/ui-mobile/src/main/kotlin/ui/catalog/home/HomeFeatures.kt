package ui.catalog.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import catalog.Feature
import catalog.label

/** The features block's own outer bounds, for a test to check it lands under the cover, not behind it. */
internal const val HOME_FEATURES_TEST_TAG = "home-features"

/**
 * The features under the cover: each one title over its own backdrop, a
 * department label above and a rule-and-deck below — a Compose port of
 * `home-features.js`. Replaces `FeatureStrip`.
 *
 * Three equal columns above [WideBreakpoint]; two below it with the first
 * spanning both (the tablet's own shape, `home.css:342-344`); one column
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
    // `min-height` (`home.css:142`, `344`): a card is `max(min, its own
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
