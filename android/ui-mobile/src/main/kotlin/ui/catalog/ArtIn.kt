package ui.catalog

import androidx.compose.runtime.MutableState
import androidx.compose.ui.Modifier
import designsystem.entrance

/** `art-in 1400ms var(--ease)` (`title-page.css:63`, `departments.css:17`). */
private const val ART_IN_MS = 1400

/** `@keyframes art-in { from { opacity: 0; transform: scale(1.04) } }` (`title-page.css:73`). */
private const val ART_IN_FROM_SCALE = 1.04f

/** The pull-quote follows its picture by this much (`title-page.css:115`, `departments.css:45`). */
internal const val QUOTE_IN_DELAY_MS = 200

/** Where a hero keeps its two art-ins in its page's [Reveals]. */
internal const val HERO_ART = "hero-art"
internal const val HERO_QUOTE = "hero-quote"

/**
 * The art-in a title spread or a department hero gives its picture and its
 * pull-quote: a fade up from the page while settling from 1.04 to rest.
 *
 * [seen] belongs to the page, never to a lazy item and never to saved state,
 * so the art-in plays once a visit: not again on a redraw (`.settled`,
 * `title-page.css:170`), a scroll back or a turn of the tablet, but again on
 * any arrival, Back included, as the web's router draws one.
 */
internal fun Modifier.artIn(seen: MutableState<Boolean>, delayMs: Int = 0): Modifier =
    entrance(ART_IN_MS, delayMs = delayMs, fromScale = ART_IN_FROM_SCALE, seen = seen)
