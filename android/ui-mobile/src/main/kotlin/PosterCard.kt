package ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import data.ProgressPoint
import data.ResumePoint
import designsystem.Spacing
import model.Progress

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
 *
 * [meta] is a fact about the title itself — an episode's show and number —
 * and sits above [caption], a fact about this viewer's own place in it
 * (`shelf-view.js`'s `card()` keeps the same two lines in the same order).
 * [progress] and [watched] draw the rule and the tick a collection card
 * never carries — only a film or a set on Continue or Next up does.
 * [held] draws the same "offline" badge `set-badge.js` draws — a title
 * this device holds in full.
 */
@Composable
internal fun PosterCard(
    posterPath: String?,
    title: String,
    caption: String?,
    modifier: Modifier,
    onClick: () -> Unit,
    meta: String? = null,
    progress: Float? = null,
    watched: Boolean = false,
    held: Boolean = false,
) {
    Column(
        modifier = modifier
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) {},
    ) {
        PosterArt(posterPath = posterPath, title = title, progress = progress, watched = watched)
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = Spacing.small),
        )
        if (meta != null) {
            Text(
                text = meta,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (caption != null) {
            Text(
                text = caption,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        if (held) OfflineBadge(modifier = Modifier.padding(top = 2.dp))
    }
}

/** [PosterCard.progress] from a raw position — shared by every screen that draws a mark from [model.WatchSnapshot]. */
internal fun watchedFractionOf(progress: Progress?): Float? =
    ResumePoint.watchedFraction(progress?.let { ProgressPoint(it.at, it.duration) })?.toFloat()
