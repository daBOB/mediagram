package ui.chrome

import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import designsystem.entrance

/** The page on screen, for a test to read its turn from: one at a time, none under the player. */
internal const val PAGE_TURN_TEST_TAG = "page-turn"

/** `main.turning { animation: page-in 420ms var(--ease) }` (`shell.css:162`). */
private const val PAGE_TURN_MS = 420

/** `@keyframes page-in { from { opacity: 0; transform: translateY(10px) } }` (`shell.css:163`). */
private val PAGE_TURN_RISE = 10.dp

/**
 * A page gone to rises into place and fades in, as the web's `main` does on
 * every navigation (`page-turn.js:33-45`). It goes on the page under the
 * chrome, never on the rail or the bar: the web's rail and masthead sit
 * outside `main` (`index.html:54,72,97`) and stay put while it turns.
 *
 * The node is new with each page, because every pushed frame and every tab
 * is keyed, so it plays once per arrival and never on a redraw of the page
 * showing. The old page goes at once with its key, so nothing cross-fades.
 * Search is keyed by its frame, not its query, so typing is never a turn
 * (`app.js:631`). The player has none: the web's player `<dialog>` opens still.
 */
internal fun Modifier.pageTurn(): Modifier {
    // ponytail: a window rebuilt from saved state (the process restored, a
    // profile chosen, the first load) composes a new node, so its first page
    // rises too; the web's first draw turns nothing. A latch in
    // LibraryBranches that marks the first page still is the upgrade.
    return entrance(PAGE_TURN_MS, fromY = PAGE_TURN_RISE).testTag(PAGE_TURN_TEST_TAG)
}
