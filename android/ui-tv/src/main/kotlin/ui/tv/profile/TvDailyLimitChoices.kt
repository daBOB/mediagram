package ui.tv.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.tv.material3.Button
import androidx.tv.material3.Text
import designsystem.Spacing
import designsystem.TvType
import model.DAILY_LIMIT_CHOICES
import model.DailyLimitWords
import model.Profile
import model.dailyLimitChoiceFor
import ui.tv.player.TvChoiceRow
import ui.tv.setup.TvDialog

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
