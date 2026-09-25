package ui.tv

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
    val interactionSource = remember { MutableInteractionSource() }
    val focused by interactionSource.collectIsFocusedAsState()

    Text(
        text = text,
        style = TvFocus.textStyle(TvTypeScale.body, focused),
        modifier =
            modifier
                .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
                .clickable(interactionSource = interactionSource, indication = null, onClick = onClick),
    )
}
