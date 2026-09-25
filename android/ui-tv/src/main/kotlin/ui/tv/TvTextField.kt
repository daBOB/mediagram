package ui.tv

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale

/**
 * One line typed through the system keyboard — the field every television
 * question and the search screen share, so a field reads the same wherever
 * the remote finds one: a plain band, the accent as its frame while the
 * remote is in it.
 *
 * [onAction] is the keyboard's own action key — [imeAction] says what that
 * key is labelled — rather than a button a D-pad would have to travel to
 * separately. [secret] masks what is typed; [placeholder] says what the
 * field is for while it is empty, the way the web's search box does.
 * [fieldModifier] reaches the text field itself, for a test tag.
 */
@Composable
internal fun TvTextField(
    value: String,
    onValue: (String) -> Unit,
    onAction: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    fieldModifier: Modifier = Modifier,
    placeholder: String? = null,
    secret: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text,
    imeAction: ImeAction = ImeAction.Done,
) {
    var focused by remember { mutableStateOf(false) }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RectangleShape,
        colors =
            SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        // Plain Surface takes one fixed Border rather than tv-material's
        // stateful focused/unfocused pair, so the accent this field
        // borrows from TvFocus comes from the local `focused` flag
        // instead of through TvFocus.surfaceBorder(), which is built
        // for the ClickableSurfaceBorder a Surface with an onClick
        // takes, not this one.
        border = TvFocus.fieldBorder(focused),
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            modifier =
                fieldModifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { focused = it.isFocused }
                    .fillMaxWidth()
                    .padding(Spacing.medium),
            textStyle = TvTypeScale.body.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
            singleLine = true,
            cursorBrush = SolidColor(Palette.Imprint),
            visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
            keyboardOptions = KeyboardOptions(imeAction = imeAction, keyboardType = keyboardType),
            keyboardActions = KeyboardActions(onDone = { onAction() }, onSearch = { onAction() }),
            decorationBox = { field ->
                Box {
                    if (value.isEmpty() && placeholder != null) {
                        Text(text = placeholder, style = TvTypeScale.body, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    field()
                }
            },
        )
    }
}
