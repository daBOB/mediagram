package ui.tv.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
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
import designsystem.Spacing
import designsystem.TvTypeScale
import java.io.File
import ui.tv.TvFocus

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
 * its own. [meta] and [caption] are the phone plate's two lines under the
 * name, in its order: [meta] a fact about the title itself (an episode's
 * show and number), [caption] one about this viewer's place in it or the
 * shelf's facts — "Next up" beside a resume line is the whole point of a
 * Home row, so a plate that dropped it would hide why it is there.
 * [progress] and [watched] are the phone plate's own two marks,
 * independent of each other the way `PosterCard`'s are: [progress] draws a
 * rule along the foot of the art, only when there is a runtime to measure
 * a position against (`catalog.watchedFractionOf` is where `null` already
 * means "cannot be measured" rather than "zero"), and [watched] draws a tick
 * — a finished title has its position cleared rather than parked at a
 * whole, so without the tick a plate watched to the end would look
 * untouched. A caller computes both exactly as the phone's callers do.
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
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
    meta: String? = null,
    caption: String? = null,
    progress: Float? = null,
    watched: Boolean = false,
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
            TvPlateArt(posterPath = posterPath, title = title, progress = progress, watched = watched)
            Text(
                text = title,
                style = TvTypeScale.body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.small, start = Spacing.small, end = Spacing.small),
            )
            meta?.let { PlateLine(it) }
            caption?.let { PlateLine(it) }
        }
    }
}

/** One quieter line under the name — [TvPlate]'s [meta][TvPlate] or caption. */
@Composable
private fun PlateLine(text: String) {
    Text(
        text = text,
        style = TvTypeScale.body,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(horizontal = Spacing.small),
    )
}

/**
 * The artwork alone, at a poster's proportions — split out the way the
 * phone's `PosterArt` is, for a page header that wants the same art
 * without a caption beneath it.
 */
@Composable
internal fun TvPlateArt(
    posterPath: File?,
    title: String,
    progress: Float?,
    watched: Boolean,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
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
        if (progress != null) {
            TvProgressRule(fraction = progress, modifier = Modifier.align(Alignment.BottomCenter))
        }
        if (watched) {
            TvWatchedTick(modifier = Modifier.align(Alignment.TopEnd))
        }
    }
}

/** A poster's own proportions, the same ratio the phone plate is cut to. */
private const val PosterAspectRatio = 2f / 3f

private val Hairline = 0.5.dp

/** How much of a plate's width one line of initials takes — see the phone plate's own constant. */
private const val InitialShare = 0.26f

/** Letters standing in for artwork are set apart, the way the phone plate's are. */
private val InitialTracking = 2.sp
