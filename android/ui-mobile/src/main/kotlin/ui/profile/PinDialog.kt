package ui.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import catalog.profile.PIN_LENGTH
import catalog.profile.PinPrompt
import designsystem.Spacing

/** The PIN field's tag, for tests. */
internal const val PinFieldTag = "pin-field"

/**
 * The one PIN dialog on the phone, for the picker and Manage alike — the
 * web's `pin-prompt.js`: four digits on the number pad, masked, nothing
 * learnt by the keyboard. The digits go the moment there are four and the
 * field empties, so a new PIN's second entry and a retry after a refusal
 * both start from nothing. [PinPrompt.heading] says what is wanted;
 * [PinPrompt.error] why the last try failed, a wait included.
 *
 * Back and a tap outside cancel, as Cancel does: a dialog is its own window,
 * so its Back reaches this before the screen's "Stay as I am" behind it.
 */
@Composable
internal fun PinDialog(
    prompt: PinPrompt,
    onPin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    // A TextFieldValue, not a String: the String overload drops an edit whose
    // text equals the last one it reported, so once the field had emptied
    // itself, the same four digits typed again — a new PIN's second entry —
    // would never arrive.
    var field by remember { mutableStateOf(TextFieldValue()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prompt.heading) },
        text = {
            // Inside the dialog's own content: a dialog is a second window,
            // and the field has to exist there before it can take focus.
            val focus = remember { FocusRequester() }
            LaunchedEffect(Unit) { focus.requestFocus() }
            Column {
                OutlinedTextField(
                    value = field,
                    onValueChange = { typed ->
                        val clean = typed.text.filter(Char::isDigit).take(PIN_LENGTH)
                        field = if (clean.length == PIN_LENGTH) TextFieldValue() else TextFieldValue(clean, TextRange(clean.length))
                        if (clean.length == PIN_LENGTH) onPin(clean)
                    },
                    // Read-only rather than disabled while the core answers: a
                    // disabled field loses focus and the keyboard with it, and
                    // a wrong PIN is usually followed straight by another try.
                    readOnly = prompt.busy,
                    label = { Text(if (prompt.newPin) "New PIN" else "PIN") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword, autoCorrectEnabled = false),
                    modifier = Modifier.focusRequester(focus).testTag(PinFieldTag),
                )
                prompt.error?.let {
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = Spacing.small).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
