package ui.tv.profile

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import designsystem.Spacing
import designsystem.TvTypeScale
import model.Profile
import ui.tv.TvTextRow
import ui.tv.setup.TvConfirmDialog
import ui.tv.setup.TvDialog

/** Tall enough for five names; more scroll inside the dialog as the remote walks down them. */
private val NamesHeight = 280.dp

/**
 * The picker's "Remove a profile…", in the phone's `RemoveProfileDialog`
 * steps and words: which profile, from every one there is, the one being
 * watched as included — and then whether, with the web's own question.
 *
 * The first step lands on the first name rather than on Cancel: choosing a
 * name removes nothing, and a remote parked on Cancel would have to climb
 * past it to reach any of them. The second step is a [TvConfirmDialog],
 * where Cancel does take the remote first and Back cancels, since that is
 * the press that costs something. Either step's Cancel leaves the whole
 * thing, as the phone's does.
 */
@Composable
internal fun TvRemoveProfileDialog(
    profiles: List<Profile>,
    onRemove: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var picked by remember { mutableStateOf<Profile?>(null) }
    val target = picked
    if (target != null) {
        TvConfirmDialog(
            title = "Remove \"${target.name}\" and everything they have watched?",
            body = "",
            confirmLabel = "Remove",
            confirm = {
                onRemove(target.id)
                onDismiss()
            },
            cancel = onDismiss,
        )
        return
    }
    val first = remember { FocusRequester() }
    val cancel = remember { FocusRequester() }
    TvDialog(
        title = "Remove which profile?",
        body = "Everything of theirs goes with it.",
        onDismissRequest = onDismiss,
        initialFocus = if (profiles.isEmpty()) cancel else first,
        content = {
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = NamesHeight).padding(top = Spacing.medium)) {
                itemsIndexed(items = profiles, key = { _, profile -> profile.id }) { index, profile ->
                    TvTextRow(
                        text = profile.name,
                        onClick = { picked = profile },
                        modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.extraSmall),
                        focusRequester = first.takeIf { index == 0 },
                    )
                }
            }
        },
    ) {
        Button(onClick = onDismiss, modifier = Modifier.focusRequester(cancel)) {
            Text("Cancel", style = TvTypeScale.body)
        }
    }
}
