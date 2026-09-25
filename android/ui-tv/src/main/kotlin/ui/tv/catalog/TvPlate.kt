package ui.tv.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.initialsOf
import coil3.compose.AsyncImage
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import java.io.File
import ui.tv.TvFocus

/** The progress rule's own tag, so a test can tell it apart from the tick it never shows alongside. */
internal const val TvPlateProgressTag = "tv-plate-progress"

/** The watched tick's own tag, for the same reason [TvPlateProgressTag] carries one. */
internal const val TvPlateWatchedTickTag = "tv-plate-watched-tick"

/**
 * One poster, for every television screen a plate is built from — Home
 * rows, shelves, kept walls, season walls alike — so a viewer meets the
 * same object everywhere on this surface, the way the phone's `PosterCard`
 * is the one plate every mobile shelf draws through.
 *
 * Named underneath rather than across its face, for the same reason the
 * phone plate is: every episode of a show carries the same artwork, and the
 * channel index pinned for a title carries none at all, so the face is the
 * least reliable place to say what something is. [initialsOf] stands in for
 * a missing poster; the name is set right beneath either way.
 *
 * [posterPath] is a resolved [File], not a lookup key — a wall opens many
 * plates at once and resolving each one's artwork is a caller concern
 * (`rememberPosterPath`), not something a single plate should each do on
 * its own. [watchedFraction] carries both marks the web plate draws from one
 * number: a fraction short of a whole finishes as a progress rule along the
 * foot of the art, and a fraction that has reached a whole finishes as a
 * tick instead — a title that is actually finished has its position cleared
 * rather than parked at `1.0`, so `watchedFraction` reaching a whole is the
 * one place in this value a caller can still say "done" rather than "here".
 *
 * A `Card`, not a plain `Surface`: tv-material's own focus scale and border
 * are built to sit on a `Card`'s container, and [TvFocus] hands the same
 * four pieces every other card on this surface takes, so this plate's
 * focus reads exactly like a profile tile's or a confirm dialog's rather
 * than inventing a fifth focus treatment.
 */
@Composable
fun TvPlate(
    title: String,
    posterPath: File?,
    watchedFraction: Float?,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onOpen,
        modifier = modifier.semantics(mergeDescendants = true) {},
        shape = TvFocus.cardShape(),
        scale = TvFocus.cardScale(),
        border = TvFocus.cardBorder(),
        glow = TvFocus.cardGlow(),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            TvPlateArt(posterPath = posterPath, title = title, watchedFraction = watchedFraction)
            Text(
                text = title,
                style = TvTypeScale.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.small, start = Spacing.small, end = Spacing.small),
            )
        }
    }
}

/**
 * The artwork alone, at a poster's proportions — split out the way the
 * phone's `PosterArt` is, since a title page will want the same art without
 * a caption beneath it once one exists on this surface.
 */
@Composable
private fun TvPlateArt(
    posterPath: File?,
    title: String,
    watchedFraction: Float?,
) {
    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxWidth()
                .aspectRatio(PosterAspectRatio)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (posterPath != null) {
            AsyncImage(
                model = posterPath,
                // The card as a whole is what a screen reader announces, and
                // it announces the title; saying it again here would make
                // every plate speak twice.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initialsOf(title),
                style = TvTypeScale.title,
                fontSize = with(LocalDensity.current) { (maxWidth * InitialShare).toSp() },
                lineHeight = with(LocalDensity.current) { (maxWidth * InitialShare * 1.1f).toSp() },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = InitialTracking,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(Spacing.small),
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .border(Hairline, MaterialTheme.colorScheme.borderVariant),
        )
        if (watchedFraction != null) {
            if (watchedFraction >= 1f) {
                TvWatchedTick(modifier = Modifier.align(Alignment.TopEnd))
            } else {
                TvProgressRule(fraction = watchedFraction, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/**
 * The progress rule along the foot of a plate. Not tv-material's own — it
 * ships no progress indicator ([ui.tv.setup.TvLoadingIndicator] hits the
 * same wall for its spinner) — so this is the plain fractional-width box
 * that stands in for one, drawn in the catalogue's one accent. [modifier]
 * carries the `align` the caller's `BoxWithConstraints` scope alone can
 * grant — a scoped modifier only resolves inside the scope that produced it,
 * not in a composable split out from it.
 */
@Composable
private fun TvProgressRule(
    fraction: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.testTag(TvPlateProgressTag).fillMaxWidth().height(ProgressHeight).background(Palette.Rule),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxSize(fraction = fraction.coerceIn(0f, 1f))
                    .background(Palette.Imprint),
        )
    }
}

/**
 * The tick a finished title draws instead of a progress rule — see
 * [TvPlate]'s own note on why the two share one [Float]. Ported from the
 * phone plate's own mark, in the same corner. [modifier] carries `align` for
 * the same reason [TvProgressRule]'s does.
 */
@Composable
private fun TvWatchedTick(modifier: Modifier = Modifier) {
    Box(
        modifier =
            modifier
                .testTag(TvPlateWatchedTickTag)
                .padding(Spacing.small)
                .size(TickSize)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = "✓", style = TvTypeScale.body, color = MaterialTheme.colorScheme.onPrimary)
    }
}

/** A poster's own proportions, the same ratio the phone plate is cut to. */
private const val PosterAspectRatio = 2f / 3f

private val TickSize = 28.dp
private val ProgressHeight = 4.dp
private val Hairline = 0.5.dp

/** How much of a plate's width one line of initials takes — see the phone plate's own constant. */
private const val InitialShare = 0.26f

/** Letters standing in for artwork are set apart, the way the phone plate's are. */
private val InitialTracking = 2.sp
