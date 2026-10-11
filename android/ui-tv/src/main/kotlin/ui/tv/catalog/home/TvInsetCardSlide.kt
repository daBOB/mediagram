package ui.tv.catalog.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import designsystem.Backdrop
import designsystem.LocalBackdrop
import designsystem.Spacing
import model.MediaSet
import ui.common.catalog.HeroArtwork
import ui.tv.catalog.TvBarClearance
import ui.tv.chrome.LocalTvPagePadding

/**
 * Google TV style's cover: the film's backdrop in a card inset from the
 * page's gutters with large rounded corners, its words at the card's foot
 * over a shade that darkens toward the start — that home screen's spotlight
 * card. The same words as every style's slide ([TvCoverWords]); the picture
 * goes under [Backdrop.SOLID] as mediagram's does. [TvHomeCover] draws the
 * buttons inside the card, padded by [InsetCardPadding].
 */
@Composable
internal fun TvInsetCardSlide(
    set: MediaSet,
    minHeight: Dp,
) {
    val page = LocalTvPagePadding.current
    Box(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .padding(start = page.start, end = page.end, top = TvBarClearance, bottom = InsetCardMargin),
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .testTag(InsetCardTag)
                    .clip(InsetCardShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        ) {
            val art = set.backdropPath?.takeIf { LocalBackdrop.current != Backdrop.SOLID }
            if (art != null) HeroArtwork(path = art, modifier = Modifier.matchParentSize())
            Box(Modifier.matchParentSize().background(Brush.horizontalGradient(0f to InsetShade, 0.65f to Color.Transparent)))
        }
        TvCoverWords(
            set = set,
            title = set.title,
            titleStyle = sansCoverTitle(long = true),
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .widthIn(max = InsetTextMaxWidth)
                    .padding(start = InsetCardPadding, end = InsetCardPadding, top = InsetCardPadding, bottom = CoverActionsReservedHeight),
        )
    }
}

/** Finds the card in a test; also a resource id on the box, through `TvShell`'s `testTagsAsResourceId`. */
internal const val InsetCardTag = "inset-cover-card"

/** The card's words and buttons stand this far in from its own edge. */
internal val InsetCardPadding = Spacing.extraLarge

/** Room under the card, so the row below starts clear of its rounded foot. */
internal val InsetCardMargin = Spacing.large

private val InsetCardShape = RoundedCornerShape(24.dp)
private val InsetShade = Color(0xE6000000)
private val InsetTextMaxWidth = 560.dp
