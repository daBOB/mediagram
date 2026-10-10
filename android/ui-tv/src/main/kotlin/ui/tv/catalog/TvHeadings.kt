package ui.tv.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import designsystem.Spacing
import designsystem.TvType
import designsystem.TvTypeScale
import designsystem.tvDp
import designsystem.tvSp
import ui.tv.TvSafeArea

/**
 * A reference page's own head — the web's `heading()` as `.shelf-head`
 * sets it (`catalog.css`), the phone's `ShelfHead`: the title in the display
 * face at [TvTypeScale.ShelfHead], how much is
 * there in small tracked capitals flush right, and a rule under both.
 * Genres, one genre, Latest, a person and a list open with this, as on the web;
 * [leading] goes before the title — a person's round portrait, which the
 * web prepends to the same header.
 */
@Composable
internal fun TvShelfHead(
    title: String,
    sub: String?,
    leading: (@Composable () -> Unit)? = null,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            leading?.let {
                it()
                Spacer(Modifier.width(Spacing.medium))
            }
            Text(
                text = title,
                style = ShelfHeadTitle,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f).padding(end = 24.tvDp).semantics { heading() },
            )
            sub?.let {
                Text(
                    text = it.uppercase(),
                    style = TvType.labelSmall.copy(letterSpacing = 0.24.em),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .padding(top = ShelfHeadRuleTop)
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(MaterialTheme.colorScheme.borderVariant),
        )
    }
}

/**
 * The same "Title · n" the web's and the phone's row and kept-wall headings
 * carry, over a hairline — one heading for Home's rows and the kept walls
 * alike, so a viewer reading Continue's row on Home and its own tab sees
 * one convention, not two. [trailing] goes at the line's far end, where a
 * Home row puts its "See all".
 */
@Composable
internal fun TvCountedHeading(
    title: String,
    total: Int,
    trailing: @Composable () -> Unit = {},
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text =
                    buildAnnotatedString {
                        append(title)
                        withStyle(SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant)) { append(" · $total") }
                    },
                style = ShelfHeadTitle,
            )
            trailing()
        }
        Box(
            modifier =
                Modifier
                    .padding(top = ShelfHeadRuleTop)
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(MaterialTheme.colorScheme.borderVariant),
        )
    }
}

/**
 * A heading one step down from [TvCountedHeading] — a course's folder, by
 * default, the web's `.season h2` (`catalog.css:71`); [style] for the other
 * headings that sit at this step.
 */
@Composable
internal fun TvSectionHeading(
    title: String,
    modifier: Modifier = Modifier,
    style: TextStyle = SeasonHeading,
) {
    Text(
        text = title,
        style = style,
        modifier = modifier.padding(top = Spacing.small),
    )
}

/**
 * A line that says something rather than leading anywhere — an empty
 * list's text where a button still follows it, so the phone's centred
 * message cannot fill the page.
 */
@Composable
internal fun TvQuietLine(
    text: String,
    modifier: Modifier = Modifier,
    textAlign: TextAlign? = null,
    style: TextStyle = TvType.bodySmall,
) {
    Text(
        text = text,
        style = style,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = textAlign,
        modifier = modifier,
    )
}

/**
 * What a screen says when it has nothing to show, in the phone's exact
 * words — a viewer who uses both surfaces reads the same sentence on each.
 */
@Composable
internal fun TvCenteredMessage(message: String) {
    TvSafeArea {
        Text(
            text = message,
            style = TvType.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

/** `.shelf-head h1` (`catalog.css:5-10`): Fraunces 600 at the television's display size for it — a reference page's head, and Stats'. */
internal val ShelfHeadTitle =
    TvType.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = TvTypeScale.ShelfHead, lineHeight = 1.em, letterSpacing = (-0.03).em)

/**
 * Fraunces 500 on its own metrics, unsized: the face the television's display
 * titles are set in, at [TvTypeScale]'s sizes. Not a [TvType] role —
 * those carry M3's line heights in sp, which a 60sp title would overrun.
 */
internal val DisplayFace = TextStyle(fontFamily = TvType.headlineSmall.fontFamily, fontWeight = FontWeight.Medium, letterSpacing = TvType.headlineSmall.letterSpacing)

/** `.season h2` (`catalog.css:71`): Geist 600 at 15px. A course's top level is set at 18 (`.season.depth-0`), [TopSeasonHeading]. */
internal val SeasonHeading = TvType.bodyMedium.copy(fontWeight = FontWeight.SemiBold)

/** `.season.depth-0 > h2` (`catalog.css:73`). */
internal val TopSeasonHeading = SeasonHeading.copy(fontSize = 18.tvSp)

/** `.shelf-sub` (`catalog.css:98`): a wall's own sections ("Films", "Series") and Search's groups — the phone's `ShelfHead` sub. */
internal val ShelfSub = TvType.headlineSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 24.tvSp, lineHeight = 1.2.em)

/** `.row-head h2` (`home.css:195`): Home's bands and a department's rows — the phone's `BandHeading`. */
internal val RowHead = TvType.bodyMedium.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.8.tvSp, lineHeight = 1.2.em, letterSpacing = (-0.01).em)
