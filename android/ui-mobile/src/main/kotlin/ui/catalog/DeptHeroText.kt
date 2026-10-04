package ui.catalog

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
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
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import designsystem.Eyebrow
import designsystem.PageTitle
import designsystem.PageTitleCompact
import designsystem.Spacing
import ui.catalog.home.OnImage
import ui.catalog.home.OnImage2
import ui.catalog.home.fluid

/** The title's own bounds, for a test to measure its real layout — text laid out is what a `text` match cannot tell apart from text wrapped or clipped. */
internal const val DEPT_HERO_TITLE_TEST_TAG = "department-hero-title"

/** The quote's own outer bounds, for a test to measure its real cap against the web's 17rem. */
internal const val DEPT_HERO_QUOTE_TEST_TAG = "department-hero-quote"

/** The tagline's own layout, for a test to check how many lines a long quote actually wraps to. */
internal const val DEPT_HERO_QUOTE_TAGLINE_TEST_TAG = "department-hero-quote-tagline"

/**
 * The huge title and facts line every [DepartmentHero] layout draws, wide
 * or compact — always in the page's own ink, never on-image: unlike the
 * quote (top-right, over the picture itself), this copy sits where the
 * art's own left fade has already blended it back to the page
 * (`departments.css` — neither `.dept-title` nor `.dept-line` names
 * `--on-image` at all, only `.dept-quote` does).
 *
 * [franchiseTitle] draws the title as `.franchise-hero .dept-title` does
 * (`departments.css:108`) rather than the plain department rule: up to
 * three lines at a smaller ceiling, instead of one line shrunk to fit —
 * a franchise's own name is never chosen the way "Movies" or "Series" are.
 */
@Composable
internal fun DeptHeroWords(
    title: String,
    line: String,
    compact: Boolean,
    width: Dp,
    franchiseTitle: Boolean = false,
    overview: String? = null,
) {
    if (franchiseTitle) {
        Text(
            text = title.uppercase(),
            style =
                PageTitle.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = fluid(41.6f, 0.055f, 80f, width.value).sp,
                    lineHeight = 0.92.em,
                ),
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() }.testTag(DEPT_HERO_TITLE_TEST_TAG),
        )
    } else {
        val titleSize = if (compact) fluid(48f, 0.16f, 72f, width.value) else fluid(56f, 0.085f, 120f, width.value)
        // A title never breaks inside a word — the web's own `.dept-title` does
        // not either, it just never has to: its `clamp()` is a plain CSS
        // formula with nothing measuring the actual string against it. Compose
        // has no such free lunch, so the size steps down to fit one line
        // instead, the same way `designsystem.PageHead` already sizes a page
        // title — [titleSize] (the web's own clamp number) is the *ceiling* this
        // starts from and shrinks below only if this catalogue's own longest
        // department name ("Documentaries") would not otherwise fit.
        BasicText(
            text = title.uppercase(),
            style = (if (compact) PageTitleCompact else PageTitle).copy(color = MaterialTheme.colorScheme.onSurface),
            autoSize = TextAutoSize.StepBased(maxFontSize = titleSize.sp),
            maxLines = 1,
            modifier = Modifier.semantics { heading() }.testTag(DEPT_HERO_TITLE_TEST_TAG),
        )
    }
    Text(
        text = line,
        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 1.4.em, fontFeatureSettings = "tnum"),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 14.dp),
    )
    // `.franchise-overview` (`departments.css`): the reading face at 17px/1.6,
    // in figures grey, no wider than 36rem.
    overview?.let {
        Text(
            text = it,
            style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 1.6.em),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 18.dp).widthIn(max = 576.dp),
        )
    }
}

/**
 * The lead title's own tagline, credited to it — `.dept-quote`
 * (`departments.css:37-51`). [onOpen] is the credit's own link, the one tap
 * target anywhere in a [DepartmentHero]; `null` draws the same words with
 * nothing to tap, matching a caller with nothing to open ([lead] absent, or
 * Documentaries' own `leadHref: null`).
 */
@Composable
internal fun DeptQuote(
    tagline: String,
    leadName: String,
    width: Dp,
    onOpen: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
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
                .padding(Spacing.medium),
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
                            it.clickable(role = Role.Button, onClickLabel = "Open $leadName", onClick = onOpen)
                        } else {
                            it
                        }
                    },
        )
    }
}
