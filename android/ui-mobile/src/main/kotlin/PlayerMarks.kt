package ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
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
import designsystem.Spacing
import player.PlayerMarksState

/**
 * The three controls the web keeps in the player — "this is where a viewer
 * is when they find out what a film actually is" (`player.js:928-929`) —
 * Watchlist, Kids, and Add to list, over [PlayerMarksState]. Absent
 * entirely with nothing open, the same as the web's three buttons before
 * `playing` is set.
 */
@Composable
internal fun PlayerMarks(marks: PlayerMarksState?, actions: PlayerMarksActions, modifier: Modifier = Modifier) {
    if (marks == null) return
    var addingToList by remember { mutableStateOf(false) }

    Row(modifier = modifier.background(Color.Black.copy(alpha = SCRIM_ALPHA)).padding(Spacing.small)) {
        MarkButton(
            label = if (marks.watchlisted) "On the list" else "Watchlist",
            onClick = actions.onToggleWatchlist,
        )
        MarkButton(
            label = if (marks.kids) "For kids" else "Kids",
            onClick = actions.onToggleKids,
        )
        MarkButton(label = "Add to list", onClick = { addingToList = true })
    }

    if (addingToList) {
        AddToListDialog(
            lists = marks.lists,
            memberOf = marks.memberOf,
            onToggle = actions.onSetInList,
            onCreate = actions.onCreateList,
            onDismiss = { addingToList = false },
        )
    }
}

/** The four writes [PlayerMarks] makes, kept as one bundle so its call site hands over one thing rather than four. */
internal data class PlayerMarksActions(
    val onToggleWatchlist: () -> Unit,
    val onToggleKids: () -> Unit,
    val onSetInList: (id: String, included: Boolean) -> Unit,
    val onCreateList: (name: String) -> Unit,
)

@Composable
private fun MarkButton(label: String, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(text = label, color = Color.White, style = MaterialTheme.typography.labelLarge)
    }
}
