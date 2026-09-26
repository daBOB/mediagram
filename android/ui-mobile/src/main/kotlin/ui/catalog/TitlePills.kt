package ui.catalog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import designsystem.Spacing

/** The smallest a control here may be, so a thumb never has to aim for less. */
private val MIN_TOUCH_TARGET = 48.dp

/**
 * The row of pills under a title's spread: what starts it, "My List", and a
 * ⋯ menu for what a page has room for but should not lead with — a Compose
 * port of `title-spread.js`'s `playPill`/`moreMenu` and `list-toggle.js`.
 * Shared by the film and the series page, which differ only in what the
 * first pill says and does — a resume label and its own destination are the
 * caller's to work out ([catalog.seriesResumeFor], [data.ResumePoint]).
 *
 * [onToggleEditorsChoice] absent hides the ⋯ menu entirely, the same as the
 * web's own `moreMenu` answering `null` for an empty item list — a kids
 * profile has nothing to put there yet, so there is nothing to open.
 */
@Composable
internal fun TitlePills(
    playLabel: String?,
    onPlay: () -> Unit,
    watchlisted: Boolean,
    onToggleWatchlist: () -> Unit,
    editorsChoicePinned: Boolean,
    onToggleEditorsChoice: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
        if (playLabel != null) {
            Button(onClick = onPlay, modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET)) {
                Text("▶ $playLabel")
            }
        }
        OutlinedButton(
            onClick = onToggleWatchlist,
            modifier =
                Modifier
                    .heightIn(min = MIN_TOUCH_TARGET)
                    .semantics { contentDescription = if (watchlisted) "Remove from My List" else "Add to My List" },
        ) {
            Text(if (watchlisted) "✓ My List" else "+ My List")
        }
        if (onToggleEditorsChoice != null) {
            var expanded by remember { mutableStateOf(false) }
            TextButton(
                onClick = { expanded = true },
                modifier = Modifier.heightIn(min = MIN_TOUCH_TARGET).semantics { contentDescription = "More" },
            ) { Text("⋯") }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    text = { Text(if (editorsChoicePinned) "Remove as editor's choice" else "Make editor's choice") },
                    onClick = {
                        expanded = false
                        onToggleEditorsChoice()
                    },
                )
            }
        }
    }
}
