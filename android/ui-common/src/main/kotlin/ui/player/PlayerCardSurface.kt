package ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import designsystem.Radius

/**
 * The player card's fill: black at 78%. Darker than [SCRIM_ALPHA], since a
 * card has no fade to lean on — its edge is a hard line across the picture.
 */
const val CARD_FILL_ALPHA = 0.78f

/** The card's hairline, about 8% white — the web card's own border. */
private const val CARD_EDGE_ALPHA = 0.08f

private val CardShape = RoundedCornerShape(Radius.card)

/**
 * The web's frosted card, without the frost: the same radius and hairline,
 * filled with a dark tint instead of a blur. A deliberate difference — the
 * video draws on its own surface, which is what keeps HDR and Dolby Vision
 * passthrough working, and blurring it would need a TextureView, which
 * breaks both. No shadow, as nowhere else in this design. The card, its
 * menus and the episode sidebar all wear this, on the phone and the
 * television alike.
 */
fun Modifier.playerCard(): Modifier =
    background(Color.Black.copy(alpha = CARD_FILL_ALPHA), CardShape)
        .border(1.dp, Color.White.copy(alpha = CARD_EDGE_ALPHA), CardShape)

/**
 * Where a card menu goes, in whatever coordinates [anchor] (the button that
 * opened it) and [card] are measured in: its bottom [gap] above the button,
 * centred on it, never out past either side of the card, and never off the
 * top — a long language list scrolls inside its own height instead.
 */
fun cardMenuOffset(anchor: IntRect, card: IntRect, menu: IntSize, gap: Int): IntOffset {
    val centred = anchor.center.x - menu.width / 2
    val x = centred.coerceIn(card.left, maxOf(card.left, card.right - menu.width))
    val y = maxOf(0, anchor.top - gap - menu.height)
    return IntOffset(x, y)
}
