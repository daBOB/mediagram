package ui.ambient

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import ui.common.ambient.AmbientTint
import kotlin.math.abs

/** A poster card as the window last saw it: its picture, and where it sits in window pixels. */
internal data class PlacedCard(
    val key: Any,
    val posterPath: String?,
    val left: Float,
    val right: Float,
    val top: Float,
    val bottom: Float,
) {
    val centreY get() = (top + bottom) / 2f
}

/**
 * The card Ambient takes its colour from: of the cards with a picture that
 * are not scrolled off the left edge, the row whose vertical centre lies
 * nearest [line], and in that row the left-most. A row is the cards whose
 * centres sit within half a card's height of the nearest one's, so a wall's
 * rows and a shelf's single row both read as rows. `null` with no such card.
 */
internal fun cardNearestLine(cards: Collection<PlacedCard>, line: Float): PlacedCard? {
    val seen = cards.filter { it.posterPath != null && it.right > 0f }
    val nearest = seen.minByOrNull { abs(it.centreY - line) } ?: return null
    val half = (nearest.bottom - nearest.top) / 2f
    return seen.filter { abs(it.centreY - nearest.centreY) <= half }.minByOrNull { it.left }
}

/**
 * Which poster the glow follows on a page of rows. The web has no Ambient, so
 * this has no counterpart there; the tablet has no focus to follow, and the
 * viewer chose a reading line about a third of the way down the window.
 * Cards report themselves through [readingLineCard] — only while Ambient is
 * on, when [LocalReadingLine] is provided — and the choice settles a moment
 * after the last report, so a fling eases through its rows instead of
 * flickering across them.
 */
@Stable
internal class ReadingLine(val tint: AmbientTint) {
    private val cards = HashMap<Any, PlacedCard>()
    private var reports by mutableIntStateOf(0)

    /** The poster path whose colour the glow should take; `null` until a card has reported. */
    var picked by mutableStateOf<String?>(null)
        private set

    /** Above zero while a title page holds the glow in its own colour; the rows keep quiet then. */
    var pins by mutableIntStateOf(0)

    fun place(card: PlacedCard) {
        if (cards[card.key] != card) {
            cards[card.key] = card
            reports++
        }
    }

    fun remove(key: Any) {
        if (cards.remove(key) != null) reports++
    }

    suspend fun follow(line: Float) {
        snapshotFlow { reports }.collectLatest {
            delay(SETTLE_MS)
            picked = cardNearestLine(cards.values, line)?.posterPath
        }
    }
}

private const val SETTLE_MS = 120L

/** Provided with [ui.common.ambient.LocalAmbientTint], by the library, only while Ambient is on. */
internal val LocalReadingLine = staticCompositionLocalOf<ReadingLine?> { null }

/** Reports this poster card to the reading line; nothing at all when Ambient is off. */
@Composable
internal fun Modifier.readingLineCard(posterPath: String?): Modifier {
    val reading = LocalReadingLine.current ?: return this
    val key = remember { Any() }
    DisposableEffect(reading, key) { onDispose { reading.remove(key) } }
    return onGloballyPositioned { coords ->
        if (coords.isAttached) {
            val r = coords.boundsInWindow()
            reading.place(PlacedCard(key, posterPath, r.left, r.right, r.top, r.bottom))
        }
    }
}
