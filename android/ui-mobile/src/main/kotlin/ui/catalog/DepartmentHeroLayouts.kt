package ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import designsystem.Spacing
import ui.catalog.home.gutterFor
import ui.pageGround

/**
 * The wide layout (`departments.css:1-51`): art fading into the page from
 * the left, copy bottom-left over it (or top-left with nothing to bleed
 * over), quote top-right.
 */
@Composable
internal fun BoxScope.WideDeptHero(
    title: String,
    line: String,
    art: String?,
    hasBackdropData: Boolean,
    quote: Pair<String, String>?,
    onOpenLead: (() -> Unit)?,
    width: Dp,
    topChrome: Dp,
    heroMinHeight: Dp,
    artFraction: Float,
    franchiseTitle: Boolean,
    overview: String?,
) {
    if (art != null) {
        // `ui.pageGround`, not `colorScheme.surface` — the window's own
        // ground, matching `MobileApp`'s own root `Surface` and `AppChrome`'s
        // own pushed-frame `Scaffold`, both of which paint that same colour.
        val paper = MaterialTheme.colorScheme.pageGround
        // `matchParentSize()`, not `fillMaxHeight()`, on this outer layer —
        // the same trap `ui.catalog.home.CoverSlide`'s own doc comment
        // already names: this hero's root `Box` only has a *floor*
        // (`heightIn(min = heroMinHeight)`), which under a `LazyColumn`
        // item's unbounded height resolves from its own children, not from
        // the incoming constraints — a child that asked to `fillMaxHeight()`
        // directly would be measuring against that unresolved bound instead
        // of the hero's own final size. `matchParentSize()` waits for the
        // hero to resolve its own height first, then hands this inner Box
        // that exact size — safe for its own children to `fillMaxWidth`/
        // `fillMaxHeight` against, which is what the art strip and its three
        // gradient layers below actually need.
        Box(Modifier.matchParentSize()) {
            val artModifier = Modifier.fillMaxHeight().fillMaxWidth(artFraction).align(Alignment.CenterEnd)
            HeroArtwork(path = art, modifier = artModifier)
            // CSS paints the *first*-declared background layer on top (the
            // same rule `CoverSlide`'s own scrim already carries) — built
            // back-to-front here to land in `.dept-art::after`'s own order
            // (`departments.css:22-28`): top fade, then bottom-ish fade,
            // then the left-into-the-page fade last, frontmost. The fourth,
            // radial layer the web draws behind all three — a dark patch
            // under the quote alone — is folded into the quote's own
            // background instead of a fourth full-width box; simpler, and
            // it only ever has to sit under that one piece of text.
            Box(artModifier.background(Brush.verticalGradient(0f to paper.copy(alpha = 0.55f), 0.24f to Color.Transparent, 1f to Color.Transparent)))
            Box(artModifier.background(Brush.verticalGradient(0f to Color.Transparent, 0.62f to Color.Transparent, 1f to paper)))
            Box(artModifier.background(Brush.horizontalGradient(0f to paper, 0.26f to paper.copy(alpha = 0.7f), 0.64f to Color.Transparent)))
        }
    }
    Column(
        modifier =
            Modifier
                .align(if (art != null) Alignment.BottomStart else Alignment.TopStart)
                // Padding before the width cap, not after — a cap applied
                // first would take the gutter back out of its own 40rem, so
                // the column measured narrower than the web's copy ever does.
                .padding(
                    start = gutterFor(width),
                    end = gutterFor(width),
                    top =
                        if (art != null) {
                            if (topChrome > 0.dp) topChrome + 12.dp else Spacing.large
                        } else {
                            // `padding-top: 64px` with nothing to lead the
                            // hero with, `40px` when Solid hides art that
                            // does exist (`departments.css:86`) — two
                            // different reasons the words sit alone, two
                            // different offsets from the masthead.
                            topChrome + (if (hasBackdropData) 40.dp else 64.dp)
                        },
                    bottom = 48.dp,
                )
                .widthIn(max = DEPT_COPY_MAX_WIDTH),
    ) {
        DeptHeroWords(title, line, compact = false, width = width, franchiseTitle = franchiseTitle, overview = overview)
    }
    if (quote != null) {
        DeptQuote(
            tagline = quote.first,
            leadName = quote.second,
            width = width,
            onOpen = onOpenLead,
            modifier =
                Modifier
                    .align(Alignment.TopEnd)
                    // `top: calc(masthead-height + 12%)` (`departments.css:40`) — the
                    // 12% is of the hero's own height, which under a `LazyColumn`
                    // item's unbounded height cannot be read live the way it can on
                    // the web; the same floor this hero reserves for itself
                    // (`heroMinHeight`) stands in, since the hero is that height in
                    // the overwhelming case (nothing here ever grows it past the
                    // floor the way a long deck can grow the cover).
                    .padding(end = gutterFor(width), top = topChrome + heroMinHeight * 0.12f)
                    // The cap sits outside the quote's own inner padding
                    // (`Spacing.medium` each side, in [DeptQuote]) the same
                    // way `right: var(--gutter)` sits outside the web's own
                    // `max-width` — without the extra room the rendered text
                    // measured a quarter narrower than the web's 17rem.
                    .widthIn(max = DEPT_QUOTE_MAX_WIDTH + Spacing.medium * 2),
        )
    }
}

/**
 * The compact layout (`departments.css:86-93`): art strip on top, title and
 * line in flow beneath — no quote, no bar term, matching the web's own
 * static masthead at this width.
 *
 * [overlap] sets the words onto the strip's own faded foot, as the web does
 * below 900px: `padding-top: 48vw` over a `64vw` strip placed behind it.
 * A phone takes it; a portrait tablet (Medium) keeps its words under the
 * strip, the hero it already had.
 */
@Composable
internal fun CompactDeptHero(
    title: String,
    line: String,
    art: String?,
    width: Dp,
    franchiseTitle: Boolean,
    overview: String?,
    overlap: Boolean,
) {
    val artHeight = width * 0.64f
    Box(Modifier.fillMaxWidth()) {
        if (art != null) {
            // Same `ui.pageGround`, the window's own ground, as the wide
            // layout's own scrim above.
            val paper = MaterialTheme.colorScheme.pageGround
            Box(Modifier.fillMaxWidth().height(artHeight)) {
                HeroArtwork(path = art, modifier = Modifier.matchParentSize())
                Box(
                    Modifier.matchParentSize().background(
                        Brush.verticalGradient(0f to Color.Transparent, 0.2f to Color.Transparent, 0.55f to paper.copy(alpha = 0.4f), 1f to paper),
                    ),
                )
            }
        }
        Column(
            modifier =
                Modifier.padding(
                    // `40px` with nothing to overlap. Never `LocalTopChrome`
                    // here: the web's compact masthead never bleeds under the
                    // hero at this width, so there is nothing above the strip
                    // to clear.
                    top =
                        when {
                            art == null -> 40.dp
                            overlap -> width * 0.48f
                            else -> artHeight + width * 0.48f
                        },
                    start = gutterFor(width),
                    end = gutterFor(width),
                    bottom = 28.dp,
                ),
        ) {
            // The web hides `.dept-quote` entirely at this width
            // (`departments.css:91`); [DeptHeroWords] never receives a
            // quote to draw on this layout as a result.
            DeptHeroWords(title, line, compact = true, width = width, franchiseTitle = franchiseTitle, overview = overview)
        }
    }
}
