package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import designsystem.Spacing
import java.io.File

/**
 * A plate, named underneath rather than across its face.
 *
 * Every episode of a show carries the same artwork, and the index pinned in
 * a channel carries no artwork at all, so the face is the least reliable
 * place to say what something is. Initials stand in for a missing poster —
 * enough to tell two plates apart at a glance, and the name is right below
 * them either way.
 *
 * The whole plate is the target, artwork and caption together. A name on
 * two lines is a third of this thing's height on a phone, and a name that
 * does not open what it names is a dead patch in the middle of a wall.
 * Merging the semantics is the other half of that: a screen reader should
 * meet one plate once, not an image and then its title again.
 */
@Composable
internal fun PosterCard(
    posterPath: String?,
    title: String,
    caption: String?,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        PosterArt(posterPath = posterPath, title = title)
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.small),
        )
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/**
 * The artwork alone, at a poster's proportions, with initials where there
 * is no artwork.
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
 * Split out of [PosterCard] because the title detail screen shows the same
 * art without a name beneath it — the name is already in the bar above. One
 * `AsyncImage` over one `File`, in one place: a second image path would be
 * free to load, scale and fall back differently from the shelves.
 */
@Composable
internal fun PosterArt(posterPath: String?, title: String, modifier: Modifier = Modifier) {
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
                fontSize = (maxWidth.value * INITIAL_SHARE).sp,
                lineHeight = (maxWidth.value * INITIAL_SHARE * 1.1f).sp,
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
    }
}

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
