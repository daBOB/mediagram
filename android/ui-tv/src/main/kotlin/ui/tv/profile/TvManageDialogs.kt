package ui.tv.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import catalog.profile.REMOVE
import catalog.profile.RESET_PIN
import catalog.profile.removeQuestion
import designsystem.Spacing
import designsystem.TvTypeScale
import model.KIDS_LIMITS
import model.Profile
import ui.tv.TvTextRow
import ui.tv.player.TvChoiceRow
import ui.tv.setup.TvConfirmDialog
import ui.tv.setup.TvDialog

/**
 * A kid's choices for its grown-up: its limit — the web's select, the
 * current one marked and holding the remote — or removal, which asks first.
 */
@Composable
internal fun TvKidActions(
    kid: Profile,
    onAge: (Int) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val current = remember { FocusRequester() }
    TvProfileActions(kid, onRemove, onDismiss, initialFocus = current) {
        KIDS_LIMITS.forEach { age ->
            TvChoiceRow(
                label = "FSK $age",
                selected = kid.kidsLimit == age,
                onClick = {
                    onAge(age)
                    onDismiss()
                },
                focusRequester = current.takeIf { kid.kidsLimit == age },
            )
        }
    }
}

/** Another grown-up, for the admin: a new PIN for them, or removal with their kids. */
@Composable
internal fun TvGrownUpActions(
    grownUp: Profile,
    onResetPin: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val first = remember { FocusRequester() }
    TvProfileActions(grownUp, onRemove, onDismiss, initialFocus = first) {
        TvTextRow(RESET_PIN, onClick = onResetPin, focusRequester = first)
    }
}

/**
 * One person's choices in a dialog under their name, Remove last. Remove asks
 * again in the web's words — a grown-up's kids go too, and the question says
 * so — with Cancel taking the remote first and Back cancelling, since that is
 * the press that costs something.
 */
@Composable
private fun TvProfileActions(
    profile: Profile,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
    initialFocus: FocusRequester,
    choices: @Composable () -> Unit,
) {
    var removing by remember { mutableStateOf(false) }
    if (removing) {
        TvConfirmDialog(
            title = removeQuestion(profile),
            body = "",
            confirmLabel = REMOVE,
            confirm = {
                onRemove()
                onDismiss()
            },
            cancel = onDismiss,
        )
        return
    }
    TvDialog(
        title = profile.name,
        body = "",
        onDismissRequest = onDismiss,
        initialFocus = initialFocus,
        content = {
            Column(Modifier.padding(top = Spacing.medium)) {
                choices()
                TvTextRow(REMOVE, onClick = { removing = true }, modifier = Modifier.padding(top = Spacing.small))
            }
        },
    ) { Button(onClick = onDismiss) { Text("Cancel", style = TvTypeScale.body) } }
}
