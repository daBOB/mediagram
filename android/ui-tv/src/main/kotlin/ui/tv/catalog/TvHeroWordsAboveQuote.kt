package ui.tv.catalog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp

/**
 * Places [words] flush against this composable's own bottom edge, and
 * [quote] top-right at [quoteTopFloor] — unless growing [words] upward from
 * the bottom would reach into that band, in which case [quote] retreats
 * to sit flush above [words] instead, or is left out entirely if even the
 * very top has no room for it. A huge title plus a long tagline can ask
 * for more combined height than [TvDepartmentHero]'s own fixed height
 * carries — unlike the tablet's own hero, tall enough on every window this
 * app runs on that its own fixed quote position (`WideDeptHero`'s own
 * `topChrome + heroMinHeight * 0.12f`) never has to move — so television
 * needs an actual rule instead of a height generous enough to never ask.
 * A plain two-`Box` layout can position either one relative to this
 * composable's own edges, but not relative to the *other's own measured
 * size* the way "stay clear of whichever is tall" needs — hence
 * [SubcomposeLayout], which measures [words] first and only then decides
 * where — or whether — [quote] fits.
 */
@Composable
internal fun TvHeroWordsAboveQuote(
    quoteTopFloor: Dp,
    gap: Dp,
    words: @Composable () -> Unit,
    quote: (@Composable () -> Unit)?,
) {
    // Whether the trial below ever found room, remembered rather than
    // decided fresh every pass: a slot [SubcomposeLayout] does not
    // subcompose in a given pass is disposed after it, but one it *did*
    // subcompose this same pass stays composed — and so still answers a
    // test's own `onNodeWithTag` — even when this measure block chooses
    // never to place it. A slot found once not to fit is asked for again
    // on the very next pass only by dropping this reset, one this
    // composable's own real callers never need: every department's hero
    // is composed fresh for that department alone, title and tagline
    // fixed for its own lifetime, not a value this same instance ever
    // recomputes a verdict for differently later.
    var quoteFits by remember { mutableStateOf(true) }
    SubcomposeLayout { constraints ->
        val loose = Constraints(maxWidth = constraints.maxWidth, maxHeight = Constraints.Infinity)
        val wordsPlaceable = subcompose(HeroLayoutSlot.Words, words).first().measure(loose)
        val wordsTop = constraints.maxHeight - wordsPlaceable.height
        val floorPx = quoteTopFloor.roundToPx()
        val gapPx = gap.roundToPx()
        val fitted =
            if (quote != null && quoteFits) {
                val placeable = subcompose(HeroLayoutSlot.Quote, quote).first().measure(loose)
                minOf(floorPx, wordsTop - gapPx - placeable.height).takeIf { top -> top >= 0 }?.let { top -> placeable to top }
            } else {
                null
            }
        if (quote != null && quoteFits && fitted == null) quoteFits = false
        layout(constraints.maxWidth, constraints.maxHeight) {
            wordsPlaceable.place(0, wordsTop)
            fitted?.let { (placeable, top) -> placeable.place(constraints.maxWidth - placeable.width, top) }
        }
    }
}

private enum class HeroLayoutSlot { Words, Quote }
