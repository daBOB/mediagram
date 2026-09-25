package ui.tv.setup

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import designsystem.Spacing
import designsystem.TvTypeScale

/**
 * The two buttons' own tags, for the androidTest set that proves initial
 * focus and Back behaviour on the real window manager (see
 * `ui-tv/src/androidTest/kotlin/ui/tv/setup/TvConfirmDialogTest.kt`).
 */
internal const val TvConfirmDialogCancelTag = "tv-confirm-dialog-cancel"
internal const val TvConfirmDialogConfirmTag = "tv-confirm-dialog-confirm"

/**
 * Every risky TV action asks the same way: a title, a body spelling out
 * what it costs, and two ways out. Cancel takes the remote's first focus —
 * never Confirm — because the two ways a person leaves this without
 * meaning to (Back, and a centre press before reading) both have to land
 * on the safe choice. `Dialog`'s own `onDismissRequest`, which the system
 * wires to Back, resolves to [cancel] for the same reason: nothing short
 * of reaching across to the other button confirms.
 *
 * [confirmLabel] has no default on purpose: every phone confirm dialog
 * names the action on its confirm button — "Start over", "Sign out",
 * "Delete list" — never a bare "Confirm", so there is no reachable default
 * here that would be right for a real call site. [cancelLabel] stays
 * generic and defaultable because the phone's own dialogs agree on that
 * one already — "Cancel" is what backing out is called everywhere.
 */
@Composable
fun TvConfirmDialog(
    title: String,
    body: String,
    confirmLabel: String,
    confirm: () -> Unit,
    cancel: () -> Unit,
    cancelLabel: String = "Cancel",
) {
    val cancelFocusRequester = remember { FocusRequester() }

    TvDialog(title = title, body = body, onDismissRequest = cancel, initialFocus = cancelFocusRequester) {
        // Cancel is drawn first and takes initial focus: the remote's
        // default direction of travel (right) then moves it toward Confirm,
        // never away from Cancel by accident.
        Button(
            onClick = cancel,
            modifier = Modifier.testTag(TvConfirmDialogCancelTag).focusRequester(cancelFocusRequester),
        ) {
            Text(cancelLabel, style = TvTypeScale.body)
        }
        Button(onClick = confirm, modifier = Modifier.testTag(TvConfirmDialogConfirmTag)) {
            Text(confirmLabel, style = TvTypeScale.body)
        }
    }
}

/**
 * The window every television dialog draws in: a title, a body, anything
 * a dialog asks beyond prose ([content]), and a row of [buttons], with
 * [initialFocus] — one of those buttons, or something in [content] —
 * taking the remote the moment it opens, so a dialog never appears with
 * nothing focused. [onDismissRequest] is what Back does. A blank [body] is
 * left out, for a dialog whose content says everything.
 */
@Composable
internal fun TvDialog(
    title: String,
    body: String,
    onDismissRequest: () -> Unit,
    initialFocus: FocusRequester,
    content: @Composable ColumnScope.() -> Unit = {},
    buttons: @Composable RowScope.() -> Unit,
) {
    Dialog(onDismissRequest = onDismissRequest) {
        // Inside the Dialog's own content, not beside it: `Dialog` composes
        // this lambda into a second window with its own composition, and a
        // FocusRequester's target has to exist in that window before
        // requesting focus on it does anything.
        LaunchedEffect(Unit) { initialFocus.requestFocus() }

        Surface(
            modifier = Modifier.keysToTheScreenBehind(),
            shape = RectangleShape,
            colors =
                SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
        ) {
            Column(modifier = Modifier.padding(Spacing.large)) {
                Text(text = title, style = TvTypeScale.title)
                if (body.isNotBlank()) {
                    Text(
                        text = body,
                        style = TvTypeScale.body,
                        modifier = Modifier.padding(top = Spacing.medium),
                    )
                }
                content()
                Row(
                    modifier = Modifier.padding(top = Spacing.large),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.medium),
                    content = buttons,
                )
            }
        }
    }
}
