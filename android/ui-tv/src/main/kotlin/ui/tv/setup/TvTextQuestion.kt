package ui.tv.setup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.tv.material3.Border
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import designsystem.Overscan
import designsystem.Palette
import designsystem.Spacing
import designsystem.TvTypeScale
import ui.tv.TvFocus

/**
 * The field's own tag, for the androidTest set that proves the focus and
 * IME-submit behaviour on the real window manager (see
 * `ui-tv/src/androidTest/kotlin/ui/tv/setup/TvTextQuestionTest.kt`) — the
 * field carries no text of its own to look it up by, unlike the dialog's
 * buttons.
 */
internal const val TvTextQuestionFieldTag = "tv-text-question-field"

/**
 * Every TV setup step is one of these: a heading naming what's being
 * asked, and a field the remote is already sitting in when the screen
 * appears. There is no separate Submit to hunt for with a D-pad — the
 * on-screen keyboard's own action key is the only way this question is
 * ever answered, so [onSubmit] is wired to nothing else.
 *
 * The field exists until sign-in can be handed over from another device
 * instead — a phone or a browser typing an `api_hash` is a far kinder
 * input method than a remote's D-pad — so this stays the plain, one-field
 * shape rather than growing shortcuts for a remote that setup does not
 * expect to be the normal way in for long.
 *
 * This is a whole screen, not a fragment dropped into one: nothing above
 * it pads for overscan on this step's behalf, so the [Overscan] margin is
 * applied here directly, the same way [ui.tv.TvSafeArea] does it for the
 * stubs it stands in for.
 */
@Composable
fun TvTextQuestion(
    prompt: String,
    value: String,
    onValue: (String) -> Unit,
    onSubmit: () -> Unit,
    secret: Boolean = false,
) {
    val focusRequester = remember { FocusRequester() }
    var focused by remember { mutableStateOf(false) }

    // The remote has nowhere else useful to land on a screen that is one
    // question: requesting focus the moment this composes is what makes
    // "point the remote at the question" true without a person hunting
    // for the field first.
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = Overscan.horizontal, vertical = Overscan.vertical),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(text = prompt, style = TvTypeScale.title)
        Surface(
            modifier = Modifier.padding(top = Spacing.large).fillMaxWidth(),
            shape = RectangleShape,
            colors =
                SurfaceDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                ),
            // Plain Surface takes one fixed Border rather than tv-material's
            // stateful focused/unfocused pair, so the accent this field
            // borrows from TvFocus is applied by hand here from the local
            // `focused` flag instead of through TvFocus.surfaceBorder(),
            // which is built for the ClickableSurfaceBorder a Surface with
            // an onClick takes, not this one.
            border =
                Border(
                    border =
                        BorderStroke(
                            TvFocus.BorderWidth,
                            if (focused) Palette.Imprint else MaterialTheme.colorScheme.border,
                        ),
                    shape = RectangleShape,
                ),
        ) {
            BasicTextField(
                value = value,
                onValueChange = onValue,
                modifier =
                    Modifier
                        .testTag(TvTextQuestionFieldTag)
                        .focusRequester(focusRequester)
                        .onFocusChanged { focused = it.isFocused }
                        .fillMaxWidth()
                        .padding(Spacing.medium),
                textStyle = TvTypeScale.body.copy(color = MaterialTheme.colorScheme.onSurfaceVariant),
                singleLine = true,
                cursorBrush = SolidColor(Palette.Imprint),
                visualTransformation = if (secret) PasswordVisualTransformation() else VisualTransformation.None,
                keyboardOptions =
                    KeyboardOptions(
                        imeAction = ImeAction.Done,
                        keyboardType = if (secret) KeyboardType.Password else KeyboardType.Text,
                    ),
                // The only way this question is ever answered: the on-screen
                // keyboard's action key, not a button a D-pad would have to
                // travel to separately.
                keyboardActions = KeyboardActions(onDone = { onSubmit() }),
            )
        }
    }
}
