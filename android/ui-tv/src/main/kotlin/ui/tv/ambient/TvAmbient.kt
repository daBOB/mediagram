package ui.tv.ambient

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import ui.common.ambient.LocalAmbientTint
import ui.common.ambient.rememberPosterColor
import java.io.File

/**
 * What a card hands its focus and its border in the Ambient style: while the
 * remote is on it, the glow behind the page takes its picture's colour, and
 * its own focused border is drawn in it. Outside Ambient, neither.
 */
internal class CardGlow(
    val modifier: Modifier,
    val border: Color?,
)

@Composable
internal fun rememberCardGlow(picture: File?): CardGlow {
    val tint = LocalAmbientTint.current ?: return NoGlow
    val colour = rememberPosterColor(picture)
    var focused by remember { mutableStateOf(false) }
    LaunchedEffect(focused, colour) { if (focused && colour != null) tint.target = colour }
    return remember(colour) { CardGlow(Modifier.onFocusChanged { focused = it.isFocused }, colour) }
}

private val NoGlow = CardGlow(Modifier, null)
