package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import designsystem.Spacing
import java.io.File

/**
 * The artwork alone, at a poster's proportions, with initials where there
 * is no artwork — split out of [PosterCard] to keep that file under the
 * project's line guideline.
 *
 * A plate rather than a card: square corners, no elevation, and a hairline
 * around the edge. On paper the web player gives this a short drop shadow
 * as well, so the plate reads as tipped onto the page; against ink a shadow
 * is invisible, and the hairline is what does that work here.
 *
 * The hairline is drawn as an overlay rather than as a border on the same
 * box, because a border modifier paints beneath the content and a poster
 * cropped to fill would cover it.
 *
 * The initials are sized from the plate rather than set once. A fixed size
 * that reads on a phone is a small mark adrift in the middle of a tablet's
 * plate, which looks like something failed rather than like a stand-in.
 *
 * That size is converted through the density rather than written in `sp`,
 * so it tracks the plate and not the system font setting. `sp` would grow
 * these letters inside a plate that does not grow with them, and at a large
 * font scale they would run out of it. Nothing is lost by it: this is
 * artwork standing in for artwork, not text, and the name it stands for is
 * set right underneath in type that scales properly.
 *
 * Split out of [PosterCard] because the title detail screen shows the same
 * art without a name beneath it — the name is already in the bar above. One
 * `AsyncImage` over one `File`, in one place: a second image path would be
 * free to load, scale and fall back differently from the shelves.
 *
 * [progress] draws a rule along the foot of the plate, only when there is a
 * runtime to measure it against — see `ResumePoint.watchedFraction`, which
 * is where `null` already means "cannot be measured" rather than "zero".
 * [watched] draws a tick instead: a finished title has no progress rule of
 * its own, because finishing clears the position that would have drawn one,
 * so without the tick a plate watched to the end would look untouched.
 * Ported from `plate.js`'s own two marks.
 */
@Composable
internal fun PosterArt(
    posterPath: String?,
    title: String,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    watched: Boolean = false,
) {
    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (posterPath != null) {
            AsyncImage(
                model = File(posterPath),
                // The plate as a whole is what a screen reader announces,
                // and it announces the title; saying it again here would
                // make every plate speak twice.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = initialsOf(title),
                style = MaterialTheme.typography.titleMedium,
                fontSize = with(LocalDensity.current) { (maxWidth * INITIAL_SHARE).toSp() },
                lineHeight = with(LocalDensity.current) { (maxWidth * INITIAL_SHARE * 1.1f).toSp() },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = INITIAL_TRACKING,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(Spacing.small),
            )
        }
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(HAIRLINE, MaterialTheme.colorScheme.outlineVariant),
        )
        if (progress != null) {
            LinearProgressIndicator(
                progress = { progress.coerceIn(0f, 1f) },
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth(),
            )
        }
        if (watched) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Spacing.small)
                    .size(TICK_SIZE)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

private val TICK_SIZE = 20.dp

/**
 * How much of a plate's width one line of initials takes. Two letters at
 * this share sit inside the plate with air around them; one letter reads as
 * a deliberate mark rather than as a missing image.
 */
private const val INITIAL_SHARE = 0.26f

/** Letters standing in for artwork are set apart, the way a plate's are. */
private val INITIAL_TRACKING = 2.sp

private val HAIRLINE = 0.5.dp

/** Two letters to stand in for artwork that is not there. */
internal fun initialsOf(title: String): String = title
    .split(WHITESPACE)
    .take(2)
    .mapNotNull { word -> word.firstOrNull(Char::isLetterOrDigit) }
    .joinToString("")
    .uppercase()
    .ifEmpty { "?" }

private val WHITESPACE = Regex("\\s+")
