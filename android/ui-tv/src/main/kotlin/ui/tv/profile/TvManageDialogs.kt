package ui.tv.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import designsystem.TvType
import model.DAILY_LIMIT_CHOICES
import model.DailyLimitWords
import model.KIDS_LIMITS
import model.Profile
import model.dailyLimitChoiceFor
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
    onDailyLimit: () -> Unit,
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
        // Its own dialog: the seven daily choices would not fit under the two ages and Remove.
        TvTextRow("${DailyLimitWords.LABEL} · ${dailyLimitChoiceFor(kid.dailyLimit).label}", onClick = onDailyLimit)
    }
}

/** A kid's daily limit: the web's select as one dialog, the current choice marked and holding the remote. */
@Composable
internal fun TvDailyLimitChoices(
    kid: Profile,
    onChoose: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    val current = remember { FocusRequester() }
    val shown = dailyLimitChoiceFor(kid.dailyLimit)
    TvDialog(
        title = "${DailyLimitWords.LABEL} · ${kid.name}",
        body = "",
        onDismissRequest = onDismiss,
        initialFocus = current,
        content = {
            Column(Modifier.padding(top = Spacing.medium)) {
                (DAILY_LIMIT_CHOICES + shown).distinct().forEach { choice ->
                    key(choice.minutes) {
                        TvChoiceRow(
                            label = choice.label,
                            selected = shown == choice,
                            onClick = {
                                if (choice != shown) onChoose(choice.minutes)
                                onDismiss()
                            },
                            focusRequester = current.takeIf { shown == choice },
                        )
                    }
                }
            }
        },
    ) { Button(onClick = onDismiss) { Text("Cancel", style = TvType.labelLarge) } }
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
    ) { Button(onClick = onDismiss) { Text("Cancel", style = TvType.labelLarge) } }
}
