package ui.tv.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.Backdrop
import designsystem.LocalBackdrop
import designsystem.LocalCatalogueTones
import designsystem.Spacing
import designsystem.TvTypeScale
import model.MediaSet
import ui.catalog.HeroArtwork
import ui.tv.chrome.LocalTvPagePadding

/**
 * A department's opening page on television — the couch twin of the
 * tablet's `DepartmentHero` (`department-hero.js`'s own port): the department's
 * name huge, one line of real figures, art fading in from the right, the
 * lead's own tagline as a pull-quote top-right.
 *
 * Fixed at [TvDepartmentHeroHeight] rather than the tablet's own fluid
 * `clamp(420px,58vh,600px)`: that clamp would stand 78% of a 540dp
 * television screen, pushing the first row below the first frame the remote
 * ever sees — this app's own risk, not the web's, since nothing here scrolls
 * a hero back into view the way a phone's swipe does. Always this height,
 * lead or not: a department with no backdrop at all is rare enough on a real
 * library that a second, shorter layout for it is not worth carrying.
 *
 * Neither the hero nor its quote is ever a focus stop, unlike the tablet's
 * own credited quote — [TvArrivalFocus]'s own callers already skip straight
 * to the first row, and a lone link top-right here would be one Up/Down
 * reach a viewer could land on only sometimes, depending on where the
 * remote happened to be. [leadName] is the quote's own credit; defaults to
 * [lead]'s title, the same fallback the tablet's hero uses for a show or a
 * course crediting its own name instead of whichever episode led.
 */
@Composable
internal fun TvDepartmentHero(
    title: String,
    line: String,
    lead: MediaSet?,
    leadName: String? = lead?.title,
    modifier: Modifier = Modifier,
) {
    // Solid hides a department's own art, the same as the tablet's hero —
    // `DepartmentHero.kt`'s own note on why the cover never does the same.
    val art = lead?.backdropPath?.takeIf { LocalBackdrop.current != Backdrop.SOLID }
    val artFraction = if (LocalBackdrop.current == Backdrop.ARTWORK) 1f else ArtWidthFraction
    val pagePadding = LocalTvPagePadding.current
    val tones = LocalCatalogueTones.current
    val paper = MaterialTheme.colorScheme.background

    Box(modifier = modifier.testTag(TvDepartmentHeroTestTag).fillMaxWidth().height(TvDepartmentHeroHeight)) {
        if (art != null) {
            Box(Modifier.matchParentSize()) {
                val artModifier = Modifier.fillMaxHeight().fillMaxWidth(artFraction).align(Alignment.CenterEnd)
                HeroArtwork(path = art, modifier = artModifier)
                // The tablet's own three-layer scrim (`WideDeptHero`, drawn
                // back-to-front the same way): top fade, bottom fade, then
                // the left-into-the-page fade last, frontmost. Its own fourth,
                // radial layer — a dark patch under the quote alone — is
                // folded into the quote's own background below instead of a
                // fourth box here, the same simplification that hero makes.
                Box(artModifier.background(Brush.verticalGradient(0f to paper.copy(alpha = 0.55f), 0.24f to Color.Transparent, 1f to Color.Transparent)))
                Box(artModifier.background(Brush.verticalGradient(0f to Color.Transparent, 0.62f to Color.Transparent, 1f to paper)))
                Box(artModifier.background(Brush.horizontalGradient(0f to paper, 0.26f to paper.copy(alpha = 0.7f), 0.64f to Color.Transparent)))
            }
        }
        TvHeroWordsAboveQuote(
            quoteTopFloor = pagePadding.top + Spacing.large,
            gap = Spacing.large,
            words = {
                Column(
                    modifier = Modifier.padding(start = pagePadding.start, end = pagePadding.end, bottom = Spacing.large).widthIn(max = HeroCopyMaxWidth),
                ) {
                    BasicText(
                        text = title.uppercase(),
                        style = TvTypeScale.title.copy(color = MaterialTheme.colorScheme.onSurface),
                        autoSize = TextAutoSize.StepBased(maxFontSize = HeroTitleSize),
                        maxLines = 1,
                        modifier = Modifier.testTag(TvDepartmentHeroTitleTestTag),
                    )
                    Text(text = line, style = TvTypeScale.body, color = tones.quiet, modifier = Modifier.padding(top = Spacing.small))
                }
            },
            quote =
                if (art != null) {
                    lead.tagline?.takeIf(String::isNotBlank)?.let { tagline ->
                        {
                            TvDeptQuote(
                                tagline = tagline,
                                leadName = leadName ?: lead.title,
                                modifier = Modifier.padding(end = pagePadding.end).widthIn(max = HeroQuoteMaxWidth),
                            )
                        }
                    }
                } else {
                    null
                },
        )
    }
}

/** The tagline's own credit, top-right over the art — never a tap target on television; see [TvDepartmentHero]'s own doc. */
@Composable
private fun TvDeptQuote(
    tagline: String,
    leadName: String,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.testTag(TvDepartmentHeroQuoteTestTag).padding(Spacing.medium)) {
        Text(
            text = "“$tagline”",
            style = TvTypeScale.body.copy(fontStyle = FontStyle.Italic),
            color = Color.White,
            maxLines = 4,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "— ${leadName.uppercase()}",
            style = TvTypeScale.body.copy(fontSize = TvTypeScale.eyebrow),
            color = Color.White.copy(alpha = 0.75f),
            modifier = Modifier.padding(top = Spacing.small),
        )
    }
}

/** [DepartmentHero]'s own art fraction (`departments.css:16`), the tablet's exact rule. */
private const val ArtWidthFraction = 0.7f

/**
 * 360dp — see [TvDepartmentHero]'s own doc on why this stands in for the
 * web's fluid clamp here. Internal, not private: every department hero is
 * this one fixed height, so [TvCatalogScreen]'s own bar blend reads it
 * directly rather than a live measurement [ui.chrome.HeroListState] would
 * otherwise exist to take — the same simplification a *fixed* height, unlike
 * Home's own cover, affords.
 */
internal val TvDepartmentHeroHeight = 360.dp

/** [DeptHeroText]'s own ceiling (`fluid(56f,0.085f,120f,width)`) evaluated at the department bar's fixed 960dp width, never read from a window television has no reason to resize. */
private val HeroTitleSize = 81.6f.sp

/**
 * Narrower than the tablet's own [DEPT_COPY_MAX_WIDTH] (640dp): that cap
 * assumes a window wide enough — the tablet's own "wide" layout starts past
 * 900dp and commonly runs well past 960 — that a 320dp quote top-right
 * never reaches into it. Television's own width is always exactly 960dp,
 * where 640 (copy) and 320 (quote), each measured from its own gutter,
 * overlap by as much as 80dp — the huge title's own name plus a long
 * tagline once actually did, on Collections. 480dp instead leaves the two
 * their own [pagePadding]-gutters plus a real, unconditional 80dp gap
 * between them, for every department's own title and quote, not only the
 * one that first showed it. Internal, not private: `TvDepartmentHeroStateTest`
 * pins the arithmetic this doc claims directly, rather than trusting a
 * rendered measurement Robolectric's own font fallback cannot be held to
 * (Fraunces measures narrower there than on a real device). Horizontal
 * only — [TvHeroWordsAboveQuote] is what keeps the two apart vertically.
 */
internal val HeroCopyMaxWidth = 480.dp

/** [DEPT_QUOTE_MAX_WIDTH]'s own cap (`departments.css:42`) — within the 480/320 split [HeroCopyMaxWidth]'s own doc guarantees never overlaps it. */
internal val HeroQuoteMaxWidth = 320.dp

/** For a test to find the hero without matching on its own words. */
internal const val TvDepartmentHeroTestTag = "tv-department-hero"

/** For a test to check the title node exists rather than guessing at its rendered text after `uppercase()`. */
internal const val TvDepartmentHeroTitleTestTag = "tv-department-hero-title"

/** For a test to measure the quote's own bounds against the title's, at the fixed 960dp width — see [HeroCopyMaxWidth]'s own doc. */
internal const val TvDepartmentHeroQuoteTestTag = "tv-department-hero-quote"
