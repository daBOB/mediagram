package ui.tv.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.Backdrop
import designsystem.LocalBackdrop
import designsystem.Overscan
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.catalog.HeroArtwork
import ui.tv.catalog.home.OnImage

/**
 * A title page's opening spread on television — `title-spread.js` and
 * `title-page.css`'s `.spread` above 900px, which a 960dp screen always is:
 * the backdrop filling the page from 28% across and fading into it from the
 * left, the title, facts and overview bottom-left over that fade, [actions]
 * (the pills that start the title) under them, and the tagline as a
 * pull-quote bottom-right over the picture. Shared by the film and the
 * series page, as the web's own module is.
 *
 * At least [TvTitleSpreadMinHeight] tall rather than the web's
 * `clamp(560px,76vh,780px)`, for [TvDepartmentHero]'s reason: that clamp
 * would fill a 540dp screen and hide the tab row, which is how a viewer
 * learns there is more to the page than the spread. A spread with no art to
 * show — none held, or Solid — shrinks to its words, as `.spread.no-art` does.
 *
 * The whole spread is one block to scroll by ([revealsFromTop]): the remote
 * coming back up to a pill brings the title above it back too.
 */
@Composable
internal fun TvTitleSpread(
    backdropPath: String?,
    title: String,
    facts: String?,
    overview: String?,
    tagline: String?,
    modifier: Modifier = Modifier,
    actions: @Composable () -> Unit,
) {
    val art = backdropPath?.takeIf { LocalBackdrop.current != Backdrop.SOLID }
    val paper = MaterialTheme.colorScheme.background
    Box(
        modifier =
            modifier
                .testTag(TvTitleSpreadTag)
                .revealsFromTop()
                .fillMaxWidth()
                .heightIn(min = if (art != null) TvTitleSpreadMinHeight else 0.dp),
    ) {
        if (art != null) {
            // `matchParentSize()`: the spread has only a floor, so the art waits for its final size.
            Box(Modifier.matchParentSize()) {
                val fraction = if (LocalBackdrop.current == Backdrop.ARTWORK) 1f else ArtWidthFraction
                val strip = Modifier.fillMaxHeight().fillMaxWidth(fraction).align(Alignment.CenterEnd)
                HeroArtwork(path = art, modifier = strip)
                // `.spread-art::after`, back to front: CSS paints its first
                // layer on top, so the left fade into the page is drawn last.
                Box(strip.background(Brush.verticalGradient(0f to paper.copy(alpha = 0.55f), 0.22f to Color.Transparent, 1f to Color.Transparent)))
                Box(strip.background(Brush.verticalGradient(0f to Color.Transparent, 0.68f to Color.Transparent, 1f to paper)))
                Box(strip.background(Brush.horizontalGradient(0f to paper, 0.3f to paper.copy(alpha = 0.88f), 0.68f to Color.Transparent)))
            }
        }
        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = Overscan.horizontal, end = Overscan.horizontal, top = Overscan.vertical, bottom = Spacing.large)
                    .widthIn(max = SpreadCopyMaxWidth),
        ) {
            val long = title.length > LongTitle
            Text(
                text = title,
                style =
                    TvTypeScale.title.copy(
                        fontSize = if (long) LongTitleSize else TitleSize,
                        lineHeight = if (long) 0.96.em else 0.92.em,
                        letterSpacing = (-0.025).em,
                    ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.semantics { heading() }.padding(bottom = Spacing.medium),
            )
            facts?.takeIf(String::isNotEmpty)?.let {
                Text(
                    text = it,
                    style = TvTypeScale.body.copy(fontFeatureSettings = "tnum"),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = Spacing.medium),
                )
            }
            overview?.takeIf(String::isNotBlank)?.let {
                // Four lines, as `.spread-overview` clamps it: the spread
                // introduces the title, it does not carry its whole synopsis.
                Text(
                    text = it,
                    style = TvTypeScale.body.copy(lineHeight = 1.5.em),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = Spacing.large),
                )
            }
            // Inside the copy's own width, as `.spread-actions` sits in `.spread-copy`:
            // a row of pills wider than it would run on under the tagline's quote.
            actions()
        }
        if (art != null && !tagline.isNullOrBlank()) {
            // `bottom: 22%` of the spread's own final height — read once it has one.
            BoxWithConstraints(Modifier.matchParentSize()) {
                Text(
                    text = "“$tagline”",
                    style =
                        TvTypeScale.body.copy(
                            fontSize = QuoteSize,
                            lineHeight = 1.2.em,
                            fontStyle = FontStyle.Italic,
                            shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 28f),
                        ),
                    color = OnImage,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .testTag(TvTitleSpreadQuoteTag)
                            .align(Alignment.BottomEnd)
                            .padding(end = Overscan.horizontal, bottom = maxHeight * 0.22f)
                            .widthIn(max = SpreadQuoteMaxWidth),
                )
            }
        }
    }
}

/** See [TvTitleSpread]'s doc on why a floor rather than the web's clamp — tall enough for the art to read, short enough for the tab row to show. */
internal val TvTitleSpreadMinHeight = 380.dp

/** `.spread-art{inset:0 0 0 28%}`. */
private const val ArtWidthFraction = 0.72f

/**
 * `.spread-copy{max-width:34rem}`. Beside a 17rem quote it leaves a real gap
 * on the 960dp screen — 48 + 544 against 960 − 48 − 272 — so the two never
 * meet, unlike the department hero's wider quote.
 */
private val SpreadCopyMaxWidth = 544.dp

/** `.spread-quote{max-width:17rem}`. */
private val SpreadQuoteMaxWidth = 272.dp

/** A title longer than this is set smaller, as `.spread-title.long` is. */
private const val LongTitle = 22

/** `clamp(3rem, 6.2vw, 5.75rem)` at the television's fixed 960dp. */
private val TitleSize = 59.5.sp

/** `clamp(2.5rem, 4.6vw, 4.25rem)` at 960dp. */
private val LongTitleSize = 44.sp

/** `clamp(1.4rem, 2.1vw, 2rem)` at 960dp — the floor, 22.4. */
private val QuoteSize = 22.4.sp

/** For a test to find the spread without matching on its own words. */
internal const val TvTitleSpreadTag = "tv-title-spread"

/** For a test to find the pull-quote. */
internal const val TvTitleSpreadQuoteTag = "tv-title-spread-quote"
