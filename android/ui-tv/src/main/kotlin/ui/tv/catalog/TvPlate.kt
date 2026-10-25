package ui.tv.catalog

import ui.tv.ambient.rememberCardGlow
import androidx.compose.ui.text.TextStyle
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import catalog.initialsOf
import coil3.compose.AsyncImage
import designsystem.Spacing
import designsystem.TvType
import designsystem.tvSp
import java.io.File
import ui.tv.TvFocus

/**
 * One poster, for every television screen a plate is built from — Home
 * rows, shelves, kept walls, franchise walls alike — so a viewer meets the
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
 * plates at once, and every one of them already arrives with its artwork
 * resolved on the [model.MediaSet] the core's own listing carried, not
 * something a single plate should each look up on its own. [meta] and
 * [caption] are the phone plate's two lines under the name, in its order:
 * [meta] a fact about the title itself (an episode's
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
 * [held] puts [TvOfflineBadge] on the art's top corner, across from the tick.
 *
 * Every plate stands as tall as its neighbours, wherever it sits: the name
 * keeps room for two lines and the caption's line is kept when there is no
 * caption, and [reserveMeta] keeps the meta's line on a wall where some
 * plates carry one (Continue's films beside its episodes). The badge rides on
 * the art for the same reason, where the web and the phone set it under the
 * lines: their plates have no box, and a line more under one of these made
 * that card taller than the rest of its row, its "Mark finished" lower.
 *
 * [inRow] says the plate sits in a row of titles rather than on a wall: in a
 * style whose rows are stills ([rowCardsAreLandscape]) it then shows
 * [backdropPath] — or the poster, cropped — at 16:9.
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
    held: Boolean = false,
    backdropPath: File? = null,
    inRow: Boolean = false,
    reserveMeta: Boolean = false,
) {
    val landscape = inRow && rowCardsAreLandscape
    val glow = rememberCardGlow(rowArtOf(landscape, posterPath, backdropPath))
    Card(
        onClick = onOpen,
        modifier = modifier.semantics(mergeDescendants = true) {}.then(glow.modifier),
        shape = TvFocus.cardShape(),
        scale = TvFocus.cardScale(),
        border = TvFocus.cardBorder(color = glow.border ?: MaterialTheme.colorScheme.primary),
        glow = TvFocus.cardGlow(),
        colors = CardDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column {
            TvPlateArt(
                posterPath = rowArtOf(landscape, posterPath, backdropPath),
                title = title,
                progress = progress,
                watched = watched,
                held = held,
                ratio = if (landscape) StillAspectRatio else PosterAspectRatio,
            )
            Text(
                text = title,
                style = PlateName,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.small, start = Spacing.small, end = Spacing.small),
            )
            if (meta != null || reserveMeta) PlateLine(meta.orEmpty())
            PlateLine(caption.orEmpty(), Modifier.padding(bottom = Spacing.small))
        }
    }
}

/** One quieter line under the name — [TvPlate]'s [meta][TvPlate] or caption; a line's height even when empty. */
@Composable
private fun PlateLine(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = PlateMeta,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        minLines = 1,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(horizontal = Spacing.small),
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
    held: Boolean = false,
    ratio: Float = PosterAspectRatio,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
                .testTag(PlateArtTag)
                .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (posterPath != null) {
            // Coil sizes the decode to this box's own constraints, so a
            // poster is read at plate size, not at the file's full size.
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
            TvPlateInitials(title = title, modifier = Modifier.fillMaxSize())
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
        if (held) {
            TvOfflineBadge(modifier = Modifier.align(Alignment.TopStart).padding(Spacing.small))
        }
    }
}

/** `.grid.plates .name` (`catalog.css:54`): Fraunces 500 at 17px. */
private val PlateName: TextStyle @Composable @ReadOnlyComposable get() = TvType.titleSmall.copy(fontSize = 17.tvSp, lineHeight = 1.25.em, letterSpacing = (-0.005).em)

/** `.grid.plates .meta` (`catalog.css:55`). */
private val PlateMeta: TextStyle @Composable @ReadOnlyComposable get() = TvType.labelSmall.copy(fontSize = 12.tvSp)

/** A poster's own proportions, the same ratio the phone plate is cut to. */
internal const val PosterAspectRatio = 2f / 3f

/** The art box of a plate, for a test to measure; a resource id on the box too. */
internal const val PlateArtTag = "plate-art"

private val Hairline = 0.5.dp
