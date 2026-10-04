package ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import designsystem.Backdrop
import designsystem.LocalBackdrop
import designsystem.PageTitle
import ui.catalog.home.OnImage
import ui.catalog.home.fluid
import ui.catalog.home.gutterFor
import ui.pageGround

/** The wide spread's own bounds, for a test to tell it from the phone's stacked one. */
internal const val WIDE_TITLE_SPREAD_TEST_TAG = "wide-title-spread"

/** `.spread-art{inset:0 0 0 28%}` (`title-page.css:63`): the art fills the spread from 28% across. */
private const val SPREAD_ART_FRACTION = 0.72f

/** `.spread-copy{max-width:34rem}`. */
private val SPREAD_COPY_MAX_WIDTH = 544.dp

/** `.spread-quote{max-width:17rem}`. */
private val SPREAD_QUOTE_MAX_WIDTH = 272.dp

/** A title longer than this is set smaller, as `.spread-title.long` is — `title-spread.js` draws the same line. */
private const val LONG_TITLE = 22

/**
 * A title page's opening spread on a wide window — `title-page.css`'s own
 * `.spread` above 900px: the backdrop filling the page from 28% across and
 * fading into it from the left, the words and the pills that start the
 * title bottom-left over that fade, and the tagline as a pull-quote
 * bottom-right over the picture itself. Built the way [WideDeptHero] is —
 * art strip, scrim layers, copy aligned over them — with the spread's own
 * gradients and sizes rather than a department's.
 *
 * No room is left for a masthead: the web's spread reaches up under its
 * departments bar, while a title page here is a pushed page under its own
 * back bar, so the art starts beneath that instead.
 */
@Composable
internal fun WideTitleSpread(
    art: String?,
    title: String,
    facts: String?,
    overview: String?,
    tagline: String?,
    actions: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val width = LocalConfiguration.current.screenWidthDp.dp
    val gutter = gutterFor(width)
    // `min-height: clamp(560px, 76vh, 780px)`; a spread with no art to show
    // (none held, or Solid) shrinks to its words, as `.spread.no-art` does.
    val minHeight = if (art != null) fluid(560f, 0.76f, 780f, LocalConfiguration.current.screenHeightDp.toFloat()).dp else 0.dp
    Box(modifier = modifier.testTag(WIDE_TITLE_SPREAD_TEST_TAG).fillMaxWidth().heightIn(min = minHeight)) {
        if (art != null) {
            val paper = MaterialTheme.colorScheme.pageGround
            // `matchParentSize()`, for the reason [WideDeptHero] names: the
            // spread has only a floor, so its art must wait for the final size.
            Box(Modifier.matchParentSize()) {
                val fraction = if (LocalBackdrop.current == Backdrop.ARTWORK) 1f else SPREAD_ART_FRACTION
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
                    // Padding before the cap, so the gutter is not taken out of the copy's own 34rem.
                    .padding(start = gutter, end = gutter, top = 48.dp, bottom = 56.dp)
                    .widthIn(max = SPREAD_COPY_MAX_WIDTH),
        ) {
            val long = title.length > LONG_TITLE
            Text(
                text = title,
                style =
                    PageTitle.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        letterSpacing = (-0.025).em,
                        fontSize = (if (long) fluid(40f, 0.046f, 68f, width.value) else fluid(48f, 0.062f, 92f, width.value)).sp,
                        lineHeight = if (long) 0.96.em else 0.92.em,
                    ),
                modifier = Modifier.semantics { heading() }.padding(bottom = 18.dp),
            )
            facts?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 16.dp),
                )
            }
            overview?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge.copy(lineHeight = 1.6.em),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(bottom = 28.dp),
                )
            }
            actions()
        }
        if (art != null && !tagline.isNullOrBlank()) {
            // `bottom: 22%` of the spread's own final height — read once it
            // has one, not guessed from the floor.
            BoxWithConstraints(Modifier.matchParentSize()) {
                Text(
                    text = "“$tagline”",
                    style =
                        MaterialTheme.typography.bodyLarge.copy(
                            fontSize = fluid(22.4f, 0.021f, 32f, width.value).sp,
                            lineHeight = 1.2.em,
                            shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 28f),
                        ),
                    fontStyle = FontStyle.Italic,
                    color = OnImage,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = gutter, bottom = maxHeight * 0.22f)
                            .widthIn(max = SPREAD_QUOTE_MAX_WIDTH),
                )
            }
        }
    }
}
