package ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import designsystem.Eyebrow
import designsystem.Spacing
import designsystem.catalogueIndication
import ui.catalog.home.OnImage
import ui.catalog.home.OnImage2
import ui.catalog.home.fluid

/** The quote's own outer bounds, for a test to measure its real cap against the web's 17rem. */
internal const val DEPT_HERO_QUOTE_TEST_TAG = "department-hero-quote"

/** The tagline's own layout, for a test to check how many lines a long quote actually wraps to. */
internal const val DEPT_HERO_QUOTE_TAGLINE_TEST_TAG = "department-hero-quote-tagline"

/**
 * The lead title's own tagline, credited to it — `.dept-quote`
 * (`departments.css:37-51`). [onOpen] is the credit's own link, the one tap
 * target anywhere in a [DepartmentHero]; `null` draws the same words with
 * nothing to tap, matching a caller with nothing to open ([lead] absent, or
 * Documentaries' own `leadHref: null`).
 * Its words settle in 200ms behind the picture, on a flag kept in the
 * hero's [reveals] and made when the quote first draws.
 */
@Composable
internal fun DeptQuote(
    tagline: String,
    leadName: String,
    width: Dp,
    onOpen: (() -> Unit)?,
    reveals: Reveals,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    // `.dept-quote`'s art-in, 200ms behind the picture (`departments.css:45`).
    val quoteIn = Modifier.artIn(reveals.of(HERO_QUOTE), delayMs = QUOTE_IN_DELAY_MS)
    // `text-shadow: 0 2px 28px rgba(0,0,0,.65)` (`departments.css:44`) — a
    // blurred drop rather than the radial patch below, which only ever
    // covers the quote's own centre; the ends of a wrapped line or the
    // attribution beneath it still need this to read over bright art.
    val quoteShadow =
        with(density) { Shadow(Color.Black.copy(alpha = 0.65f), Offset(0f, 2.dp.toPx()), blurRadius = 28.dp.toPx()) }
    Column(
        modifier =
            modifier
                .testTag(DEPT_HERO_QUOTE_TEST_TAG)
                // The web's fourth, radial background layer — a dark patch
                // under the quote alone, so `OnImage`'s fixed light colour
                // stays legible over whatever the picture is doing there, in
                // either theme. `drawBehind` rather than a plain
                // `background()` brush: only it hands back the box's own
                // measured [androidx.compose.ui.geometry.Size], which a
                // fixed CSS ellipse does not need but this radius does —
                // `Brush.radialGradient`'s own default (`minDimension / 2`)
                // drew a patch too small to reach the attribution line or a
                // wrapped quote's own ends.
                .drawBehind {
                    drawRect(Brush.radialGradient(listOf(Color(0x9E080809), Color(0x00080809)), radius = size.maxDimension * 0.6f))
                }
                .padding(Spacing.medium)
                // Inside the patch: the web's patch belongs to the art's still
                // `::after` (`departments.css:27`), so only the words settle in.
                .then(quoteIn),
    ) {
        Text(
            text = "“$tagline”",
            style =
                // `FontStyle.Italic` here slants the upright face rather
                // than switching to a true italic cut — `designsystem.Read`
                // (Newsreader) ships only the upright `newsreader.ttf`; the
                // web loads a separate `newsreader-italic-*.woff2` this
                // catalogue's own font set does not carry. Faux-slanting a
                // serif is a legible fallback, not a match, until an italic
                // resource is added.
                MaterialTheme.typography.bodyLarge.copy(
                    fontStyle = FontStyle.Italic,
                    fontSize = fluid(20.8f, 0.019f, 28.8f, width.value).sp,
                    lineHeight = 1.2.em,
                    shadow = quoteShadow,
                ),
            color = OnImage,
            // The web draws every line of the tagline it was given, no
            // clamp — a ceiling well past any real tagline stands in for
            // "uncapped" rather than an actual unbounded `maxLines`, which
            // `Text` cannot express directly.
            maxLines = 6,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag(DEPT_HERO_QUOTE_TAGLINE_TEST_TAG),
        )
        Text(
            text = "— $leadName".uppercase(),
            style = Eyebrow.copy(fontSize = 10.sp, letterSpacing = 0.28.em, lineHeight = 1.6.em, shadow = quoteShadow),
            color = OnImage2,
            modifier =
                Modifier
                    .padding(top = 14.dp)
                    // `Role` has no `Link` value to reach for — `Button` is
                    // the same choice the hero's own, now-removed whole-hero
                    // tap target already made for the same reason: a real
                    // link's role would name this more precisely than
                    // TalkBack's generic "button" does, but nothing in this
                    // enum expresses it.
                    .let {
                        if (onOpen != null) {
                            // A link on the web (`department-hero.js:30`), and links don't shrink when pressed.
                            it.clickable(interactionSource = null, indication = catalogueIndication(pressScale = 1f), onClickLabel = "Open $leadName", role = Role.Button, onClick = onOpen)
                        } else {
                            it
                        }
                    },
        )
    }
}
