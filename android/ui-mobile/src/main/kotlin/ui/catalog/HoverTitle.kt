package ui.catalog

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.takeOrElse
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import designsystem.Motion

/**
 * A title that turns accent over `--hover` while its card or row is hovered
 * (`catalog.css:44,48` for a card's name, `:76-78` for a row's). The web's
 * rule is a bare `:hover`, which a finger also raises while it is down, so a
 * press stands in for it here. Its own scope, so only this Text recomposes
 * while the colour fades. [source] is the one the card's click uses.
 */
@Composable
internal fun HoverTitle(
    text: String,
    style: TextStyle,
    source: MutableInteractionSource,
    modifier: Modifier = Modifier,
    rest: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
    overflow: TextOverflow = TextOverflow.Clip,
) {
    val hovered by source.collectIsHoveredAsState()
    val pressed by source.collectIsPressedAsState()
    val ink by animateColorAsState(
        if (hovered || pressed) MaterialTheme.colorScheme.primary else rest.takeOrElse { LocalContentColor.current },
        animationSpec = Motion.hover(),
        label = "title ink",
    )
    Text(text = text, style = style, color = ink, maxLines = maxLines, overflow = overflow, modifier = modifier)
}
