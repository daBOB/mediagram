package ui.tv.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.key.KeyEvent
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import designsystem.Spacing
import designsystem.TvTypeScale
import player.KIDS_CHOICES
import player.PlayerMarksState
import player.PlayerViewModel
import player.setKidsMark
import ui.tv.setup.LocalTvDialogKeys
import ui.tv.setup.TvDialog

/**
 * The Kids choice over the film — the web's select ("For kids": Not for
 * kids, From 6, From 12) as a television dialog, the current answer marked
 * and holding the remote. Choosing closes it; Cancel and Back leave the mark
 * as it was.
 */
@Composable
internal fun TvKidsChoiceDialog(
    current: Int?,
    onChoose: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val chosen = remember { FocusRequester() }
    TvDialog(
        title = "For kids",
        body = "",
        onDismissRequest = onDismiss,
        initialFocus = chosen,
        content = {
            Column(Modifier.padding(top = Spacing.medium)) {
                KIDS_CHOICES.forEach { choice ->
                    TvChoiceRow(
                        label = choice.label,
                        selected = choice.age == current,
                        onClick = {
                            onChoose(choice.age)
                            onDismiss()
                        },
                        focusRequester = chosen.takeIf { choice.age == current },
                    )
                }
            }
        },
    ) { Button(onClick = onDismiss) { Text("Cancel", style = TvTypeScale.body) } }
}

/** The Kids choice over the film while a title is open; its window's keys go to the player first, as the list dialog's do. */
@Composable
internal fun TvKidsChoiceOverPlayer(
    marks: PlayerMarksState?,
    viewModel: PlayerViewModel,
    onDismiss: () -> Unit,
    keys: (KeyEvent) -> Boolean,
) {
    val open = marks ?: return
    CompositionLocalProvider(LocalTvDialogKeys provides keys) {
        TvKidsChoiceDialog(current = open.kidsMark, onChoose = viewModel::setKidsMark, onDismiss = onDismiss)
    }
}
