package ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.constrainWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import androidx.window.core.layout.WindowWidthSizeClass
import designsystem.Backdrop
import designsystem.LocalBackdrop
import designsystem.Spacing

private val HERO_HEIGHT = 260.dp

/**
 * Caps the art at 40% of the screen's own height on top of [HERO_HEIGHT]: a
 * phone in portrait never comes near it, so the look there is unchanged,
 * but a short landscape phone keeps the title, facts and tabs above the fold
 * instead of losing them to a fixed-height block.
 */
private const val HERO_HEIGHT_FRACTION = 0.4f

/**
 * A title page's opening spread — a Compose port of `title-spread.js`'s own
 * `titleSpread`/`describeHero`: the backdrop filling the block and fading
 * into the page behind the words, the title, facts and overview, then
 * [actions] — the pills that start the title. Shared by the film and the
 * series page, exactly as the web's own module is.
 *
 * A wide window (the same EXPANDED class the web's 900px breakpoint maps to
 * everywhere else here) follows the web's own spread: [WideTitleSpread],
 * the words bottom-left over the art's fade and the tagline as a pull-quote
 * bottom-right. A phone, or a tablet in portrait, does not have the width
 * for words beside a picture: the words sit below the art instead, which
 * is the same "read the words, see the picture behind them" idea in the
 * shape this screen has room for — a deliberate difference from the web's
 * ≤900px spread, which lays its words over the art's lower edge. The
 * tagline quote is left out there, as the web leaves it out below 900px.
 *
 * [bleed] is the horizontal padding of whatever list holds the spread: the
 * wide spread reaches back over it so its art meets the window's edge, as
 * the web's does. The stacked spread keeps the inset — see the series page.
 */
@Composable
internal fun TitleSpread(
    backdropPath: String?,
    title: String,
    facts: String?,
    overview: String?,
    tagline: String?,
    modifier: Modifier = Modifier,
    bleed: Dp = 0.dp,
    actions: @Composable () -> Unit = {},
) {
    // Solid drops the art — no picture, no placeholder, no tagline quote —
    // the same "plain pages, no artwork" the web's own
    // `[data-backdrop="solid"]` rule leaves behind.
    val art = backdropPath?.takeIf { LocalBackdrop.current != Backdrop.SOLID }
    if (currentWindowAdaptiveInfo().windowSizeClass.windowWidthSizeClass == WindowWidthSizeClass.EXPANDED) {
        WideTitleSpread(art, title, facts, overview, tagline, actions, modifier.bleed(bleed))
        return
    }
    val background = MaterialTheme.colorScheme.background
    val heroHeight = minOf(HERO_HEIGHT, (LocalConfiguration.current.screenHeightDp * HERO_HEIGHT_FRACTION).dp)
    Column(modifier = modifier.fillMaxWidth()) {
        if (LocalBackdrop.current != Backdrop.SOLID) {
            Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
                if (art != null) {
                    HeroArtwork(path = art, modifier = Modifier.matchParentSize())
                    // Fades the artwork into the page along its foot, the same
                    // job the web's own CSS fade does over its own axis.
                    Box(
                        modifier =
                            Modifier
                                .matchParentSize()
                                .background(Brush.verticalGradient(listOf(Color.Transparent, background))),
                    )
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
            Box(modifier = Modifier.padding(top = Spacing.medium)) { actions() }
        }
    }
}

/** Widens a node by [bleed] on each side of the slot it was given, centred over it. */
private fun Modifier.bleed(bleed: Dp): Modifier =
    if (bleed == 0.dp) {
        this
    } else {
        layout { measurable, constraints ->
            val extra = bleed.roundToPx()
            val placeable = measurable.measure(constraints.offset(horizontal = extra * 2))
            layout(constraints.constrainWidth(placeable.width - extra * 2), placeable.height) {
                placeable.place(-extra, 0)
            }
        }
    }
