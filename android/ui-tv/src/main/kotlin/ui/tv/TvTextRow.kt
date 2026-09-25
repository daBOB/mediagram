package ui.tv

import androidx.compose.foundation.clickable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.tv.material3.Text
import designsystem.TvTypeScale

/**
 * One focusable text row for every TV screen that needs a menu line rather
 * than a card — "Start over", a library choice, "Try again", "Stay as I
 * am" — so the focus treatment (the accent colour plus the underline
 * [TvFocus.textStyle] draws) reads the same wherever one of these appears,
 * the same reason [TvFocus] itself exists for cards. Layout is entirely the
 * caller's: [modifier] carries whatever width, padding and tag each row
 * needs, since those differ by where the row sits, and this only owns the
 * focus and click behaviour common to all of them.
 */
@Composable
internal fun TvTextRow(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
) {
    // Read from the focus state itself rather than from the click's
    // interaction stream: a row focused in the same moment a centre press
    // opened its page never heard the focus interaction — it held the
    // remote while looking exactly as if it did not.
    var focused by remember { mutableStateOf(false) }

    Text(
        text = text,
        style = TvFocus.textStyle(TvTypeScale.body, focused),
        modifier =
            modifier
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                .onFocusChanged { focused = it.isFocused }
                .clickable(indication = null, interactionSource = null, onClick = onClick),
    )
}
