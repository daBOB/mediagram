package ui.player

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import model.KidsVerdict
import player.KIDS_CHOICES
import player.PlayerMarksState
import player.kidsLabel
import player.listLabel

/**
 * The three controls the web keeps in the player — "this is where a viewer
 * is when they find out what a film actually is" (`player.js:928-929`) —
 * My List, Kids, and Add to list, over [PlayerMarksState]. Absent
 * entirely with nothing open, the same as the web's three buttons before
 * `playing` is set.
 */
@Composable
internal fun PlayerMarks(
    marks: PlayerMarksState?,
    actions: PlayerMarksActions,
    modifier: Modifier = Modifier,
    notice: String? = null,
) {
    if (marks == null) return
    var addingToList by remember { mutableStateOf(false) }

    Row(modifier = modifier) {
        MarkButton(
            label = listLabel(marks),
            onClick = actions.onToggleWatchlist,
        )
        if (marks.canMarkKids) KidsMark(marks, onChoose = actions.onKidsMark)
        MarkButton(label = "Add to list", onClick = { addingToList = true })
    }

    if (addingToList) {
        AddToListDialog(
            lists = marks.lists,
            memberOf = marks.memberOf,
            onToggle = actions.onSetInList,
            onCreate = actions.onCreateList,
            onDismiss = { addingToList = false },
            notice = notice,
        )
    }
}

/** The four writes [PlayerMarks] makes, kept as one bundle so its call site hands over one thing rather than four. */
internal data class PlayerMarksActions(
    val onToggleWatchlist: () -> Unit,
    /** The age an unrated title is for kids from — 6 or 12 — or null for not for kids. */
    val onKidsMark: (age: Int?) -> Unit,
    val onSetInList: (id: String, included: Boolean) -> Unit,
    val onCreateList: (name: String) -> Unit,
)

/**
 * The Kids control: on an unrated title the web's select — "Not for kids",
 * "From 6", "From 12" — as a menu under the button, which names the current
 * choice; on a rated title its verdict, dimmed, with nothing to press.
 */
@Composable
private fun KidsMark(
    marks: PlayerMarksState,
    onChoose: (Int?) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box {
        MarkButton(label = kidsLabel(marks), onClick = { open = true }, enabled = marks.kidsVerdict == KidsVerdict.UNRATED)
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            KIDS_CHOICES.forEach { choice ->
                DropdownMenuItem(
                    text = { Text(choice.label) },
                    onClick = {
                        open = false
                        if (choice.age != marks.kidsMark) onChoose(choice.age)
                    },
                )
            }
        }
    }
}

@Composable
private fun MarkButton(
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
) {
    TextButton(onClick = onClick, enabled = enabled) {
        // Dimmed rather than hidden when it cannot be pressed: the rating
        // it reports is still worth reading.
        Text(
            text = label,
            color = if (enabled) Color.White else Color.White.copy(alpha = DISABLED_ALPHA),
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

private const val DISABLED_ALPHA = 0.7f
