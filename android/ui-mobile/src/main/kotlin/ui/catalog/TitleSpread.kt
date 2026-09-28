package ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.window.core.layout.WindowWidthSizeClass
import designsystem.Backdrop
import designsystem.LocalBackdrop
import designsystem.Spacing
import ui.catalog.home.OnImage

private val HERO_HEIGHT = 260.dp

/**
 * Caps the art at 40% of the screen's own height on top of [HERO_HEIGHT]: a
 * phone in portrait never comes near it, so the look there is unchanged,
 * but a short landscape phone keeps the title, facts and tabs above the fold
 * instead of losing them to a fixed-height block.
 */
private const val HERO_HEIGHT_FRACTION = 0.4f
private val QUOTE_MAX_WIDTH = 220.dp

/**
 * A title page's opening spread — a Compose port of `title-spread.js`'s own
 * `titleSpread`/`describeHero`: the backdrop filling the block and fading
 * into the page behind the words, the tagline set large over the artwork as
 * a pull-quote, and the title, facts and overview below it. Shared by the
 * film and the series page, exactly as the web's own module is.
 *
 * The web sets the words down the left and the art filling the right, a
 * layout a magazine page has the width for. A phone does not: the words sit
 * below the art here instead, which is the same "read the words, see the
 * picture behind them" idea in the shape this screen actually has room for
 * — a deliberate difference from the web's own two-column spread, not a
 * partial port of it. The tagline quote is the web's own rule, not a
 * difference: shown on a wide window, left out below 900dp as the web
 * leaves it out below 900px.
 */
@Composable
internal fun TitleSpread(
    backdropPath: String?,
    title: String,
    facts: String?,
    overview: String?,
    tagline: String?,
    modifier: Modifier = Modifier,
) {
    val background = MaterialTheme.colorScheme.background
    val heroHeight = minOf(HERO_HEIGHT, (LocalConfiguration.current.screenHeightDp * HERO_HEIGHT_FRACTION).dp)
    // The web hides `.spread-quote` below 900px (`title-page.css:161`), read
    // from the same width signal `DepartmentHero` hides its own quote by.
    val wide = currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED
    Column(modifier = modifier.fillMaxWidth()) {
        // Solid drops the whole block — no art, no placeholder, no tagline
        // quote — the same "plain pages, no artwork" the web's own
        // `[data-backdrop="solid"]` rule leaves behind.
        if (LocalBackdrop.current != Backdrop.SOLID) {
            Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
                if (backdropPath != null) {
                    HeroArtwork(path = backdropPath, modifier = Modifier.matchParentSize())
                    // Fades the artwork into the page along its foot, the same
                    // job the web's own CSS fade does over its own axis.
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, background))),
                    )
                    if (wide && !tagline.isNullOrBlank()) {
                        Text(
                            text = "“$tagline”",
                            // Over the artwork, so the web's own `--on-image`
                            // and text shadow rather than the page's ink,
                            // which vanished against bright art in Light.
                            style =
                                MaterialTheme.typography.headlineSmall.copy(
                                    shadow = Shadow(color = Color.Black.copy(alpha = 0.65f), offset = Offset(0f, 2f), blurRadius = 28f),
                                ),
                            fontStyle = FontStyle.Italic,
                            color = OnImage,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                            modifier =
                                Modifier
                                    .align(Alignment.TopEnd)
                                    .widthIn(max = QUOTE_MAX_WIDTH)
                                    .padding(Spacing.large),
                        )
                    }
                } else {
                    Box(modifier = Modifier.matchParentSize().background(MaterialTheme.colorScheme.surfaceVariant))
                }
            }
        }
        Column(modifier = Modifier.padding(PaddingValues(horizontal = Spacing.large, vertical = Spacing.medium))) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                fontFamily = FontFamily.Serif,
            )
            facts?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = Spacing.extraSmall),
                )
            }
            overview?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = Spacing.medium),
                )
            }
        }
    }
}
