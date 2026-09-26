package ui.catalog

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import designsystem.Spacing
import model.MediaSet
import java.io.File

/** The kicker's own letterspacing — a spaced-caps eyebrow, `department-hero.js`'s own look. */
private val KICKER_TRACKING = 1.sp

/**
 * A department's opening page, the way a magazine opens its cinema or its
 * television section — a Compose port of `department-hero.js`'s own
 * `departmentHero`: a spaced-caps kicker, the department's name set very
 * large, one line of real figures, over a backdrop from inside it. Shared by
 * Movies, Series, Tutorials, Collections and one franchise's own page.
 *
 * [lead]'s tagline is not drawn here — a caller that has one credits it with
 * [PullQuote] underneath, the same component the magazine home page already
 * uses, rather than a second copy of that block.
 */
@Composable
internal fun DepartmentHero(
    kicker: String,
    title: String,
    line: String,
    lead: MediaSet?,
    onOpenTitle: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val art = lead?.backdropPath
    Box(
        modifier = modifier
            .fillMaxWidth()
            .let { if (art != null) it.aspectRatio(16f / 9f) else it }
            .let { if (lead != null) it.clickable(role = Role.Button) { onOpenTitle(lead.setId) } else it },
    ) {
        if (art != null) {
            AsyncImage(
                model = File(art),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
            )
        }
        Column(
            modifier = Modifier
                .align(if (art != null) Alignment.BottomStart else Alignment.TopStart)
                .padding(Spacing.large),
        ) {
            Text(
                text = kicker.uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = if (art != null) Color.White.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = KICKER_TRACKING,
            )
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (art != null) Color.White else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = Spacing.small),
            )
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = if (art != null) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = Spacing.extraSmall),
            )
        }
    }
}

/** A row's name, with an optional "See all" and an optional control beside it — the Compose port of `deptRow`'s own header. */
@Composable
internal fun DeptRowHeading(
    title: String,
    onSeeAll: (() -> Unit)? = null,
    seeAllLabel: String = "See all",
    extra: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Spacing.medium, vertical = Spacing.small),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        extra?.invoke()
        if (onSeeAll != null) {
            Text(
                text = seeAllLabel,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.clickable(role = Role.Button, onClick = onSeeAll).padding(start = Spacing.medium),
            )
        }
    }
}
