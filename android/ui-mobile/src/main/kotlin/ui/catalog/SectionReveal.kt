package ui.catalog

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import designsystem.entrance

/** `.motion .reveal`: rises over 1100ms and fades in over 900ms, both on `--ease` (`theme.css:208`). */
private const val REVEAL_RISE_MS = 1_100
private const val REVEAL_FADE_MS = 900
private val REVEAL_FROM = 28.dp

/**
 * The sections of one page that have come into view on this visit, by key:
 * the web's `.reveal.in` (`reveal.js:38-45`).
 *
 * Held by the page's own composition, not by its lazy items and not in
 * saved state. A lazy item scrolled away and back is composed anew and finds
 * its section here, so it comes back at rest. A page left and come back to
 * is a new visit, which the web reveals afresh (`reveal.js:32`); the shelves'
 * saved state outlives a title opened over them, so a saved flag would not.
 */
internal class Reveals {
    private val seen = HashMap<String, MutableState<Boolean>>()

    fun of(key: String): MutableState<Boolean> = seen.getOrPut(key) { mutableStateOf(false) }
}

/** One [Reveals] for as long as the page is on screen. */
@Composable
internal fun rememberReveals(): Reveals {
    // ponytail: a window rebuilt from saved state (the process restored)
    // rebuilds this with it, so the sections in view rise again. Rotation
    // doesn't: MainActivity handles orientation itself.
    return remember { Reveals() }
}

/**
 * The web's scroll reveal (`theme.css:208-209`): a section rises 28dp and
 * fades in the first time it comes into view on a visit. It starts when the
 * lazy list first places the item, never when it composes one ahead of the
 * viewport. Scrolled back to, or redrawn by a sync with the same keys, it is
 * already in. Under "Remove animations" it simply shows, as the web's
 * reduced motion does (`reveal.js:15-18`).
 *
 * Items that together make one web section (a grid's cells, a run of tile
 * lines) share one [key]: those placed with the first rise with it, and the
 * ones scrolled to after are already in, as the rest of a revealed web
 * section is.
 */
internal fun Modifier.reveal(reveals: Reveals, key: String): Modifier {
    // ponytail: a section that a refresh adds while the page shows rises as
    // it is placed; the web's redraw shows it at once (`reveal.js:32-36`).
    return entrance(REVEAL_RISE_MS, fromY = REVEAL_FROM, fadeMs = REVEAL_FADE_MS, seen = reveals.of(key))
}

/** A full-width section of a lazy column that reveals as it comes into view, [key] naming it both as an item and as a section. */
internal fun LazyListScope.revealItem(reveals: Reveals, key: String, content: @Composable () -> Unit) {
    item(key = key) { Box(Modifier.reveal(reveals, key)) { content() } }
}

/** [revealItem] for a lazy grid: one section across every column. */
internal fun LazyGridScope.revealItem(reveals: Reveals, key: String, content: @Composable () -> Unit) {
    item(key = key, span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.reveal(reveals, key)) { content() } }
}
