package ui.tv.catalog

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import model.Kind
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import playback.FilmPreloadRow
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [TvPreloadsPage]: what is preloading, queued, or already fully on this
 * device — the television twin of [ui.catalog.PreloadsScreen]. Real D-pad
 * key events, the same [TvScreenStateTest] harness every other catalogue
 * page's own state test uses.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvPreloadsPageTest : TvScreenStateTest() {

    @Test
    fun emptyEverythingSaysNothingIsPreloading() {
        show { TvPreloadsPage(rows = emptyList(), heldFilms = emptyList(), onOpenTitle = {}, onCancel = {}, onRemove = {}, onResume = {}) }
        compose.onNodeWithText("Nothing is preloading right now.").assertExists()
    }

    @Test
    fun preloadingRowShowsItsOwnLabelAndBarAndOpensTheTitleThenCancelsFromTheRight() {
        var opened: String? = null
        var cancelled: String? = null
        val row = FilmPreloadRow.Running("f1", "Der Pate", TOTAL, heldBytes = HELD_40_PERCENT, pauseReason = null)
        show {
            TvPreloadsPage(
                rows = listOf(row), heldFilms = emptyList(),
                onOpenTitle = { opened = it }, onCancel = { cancelled = it }, onRemove = {}, onResume = {},
            )
        }
        // "Preloading" names both the section heading and this one row's own
        // status (it carries no pause reason) — two nodes by design, not one.
        compose.onAllNodesWithText("Preloading").assertCountEquals(2)
        compose.onNodeWithText("Der Pate").assertIsFocused()
        compose.onNodeWithText("2.0 of 5.0 GB · 40%").assertExists()

        compose.onNodeWithText("Der Pate").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("f1", opened)

        compose.onNodeWithText("Der Pate").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Cancel").assertIsFocused()
        compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("f1", cancelled)
    }

    @Test
    fun queuedRowShowsInItsOwnSectionAndCancelReachesItsId() {
        var cancelled: String? = null
        val rows = listOf(FilmPreloadRow.Waiting("f2", "The Green Mile", TOTAL))
        show { TvPreloadsPage(rows = rows, heldFilms = emptyList(), onOpenTitle = {}, onCancel = { cancelled = it }, onRemove = {}, onResume = {}) }
        compose.onNodeWithText("Queued").assertExists()
        compose.onNodeWithText("The Green Mile").assertIsFocused()
        compose.onNodeWithText("The Green Mile").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Cancel").assertIsFocused()
        compose.onNodeWithText("Cancel").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("f2", cancelled)
    }

    @Test
    fun queuedFilmsListInTheEnginesOwnOrder() {
        val rows = listOf(FilmPreloadRow.Waiting("f2", "The Green Mile", TOTAL), FilmPreloadRow.Waiting("f3", "Le Mans 66", TOTAL))
        show { TvPreloadsPage(rows = rows, heldFilms = emptyList(), onOpenTitle = {}, onCancel = {}, onRemove = {}, onResume = {}) }
        val greenMileTop = compose.onNodeWithText("The Green Mile").fetchSemanticsNode().boundsInRoot.top
        val leMansTop = compose.onNodeWithText("Le Mans 66").fetchSemanticsNode().boundsInRoot.top
        assertTrue(greenMileTop < leMansTop)
    }

    @Test
    fun onThisDeviceListsHeldFilmsAndRemoveReachesTheRightOne() {
        var removed: String? = null
        val held = set("held-1", Kind.MOVIE, "Chihiros Reise ins Zauberland", addedAt = 0)
        show { TvPreloadsPage(rows = emptyList(), heldFilms = listOf(held), onOpenTitle = {}, onCancel = {}, onRemove = { removed = it }, onResume = {}) }
        compose.onNodeWithText("On this device").assertExists()
        compose.onNodeWithText("Chihiros Reise ins Zauberland").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Remove").assertIsFocused()
        compose.onNodeWithText("Remove").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("held-1", removed)
    }

    /** The user decision: a film the background time limit paused stays under Preloading (it was the one writing), named plainly, with one Resume action rather than Cancel. */
    @Test
    fun aTimeLimitPausedFilmThatWasActiveStaysUnderPreloadingWithResume() {
        var resumed: FilmPreloadRow.TimeLimitPaused? = null
        val row = FilmPreloadRow.TimeLimitPaused("f1", "Der Pate", TOTAL, heldBytes = HELD_40_PERCENT, wasActive = true)
        show { TvPreloadsPage(rows = listOf(row), heldFilms = emptyList(), onOpenTitle = {}, onCancel = {}, onRemove = {}, onResume = { resumed = it }) }
        compose.onNodeWithText("Preloading").assertExists()
        compose.onNodeWithText("Der Pate").assertIsFocused()
        compose.onNodeWithText("Paused — background limit").assertExists()

        compose.onNodeWithText("Der Pate").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Resume").assertIsFocused()
        compose.onNodeWithText("Resume").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(row, resumed)
    }

    /** As above, for a film that was merely queued when the pause hit — it stays under Queued, not Preloading. */
    @Test
    fun aTimeLimitPausedFilmThatWasQueuedStaysUnderQueuedWithResume() {
        var resumed: FilmPreloadRow.TimeLimitPaused? = null
        val row = FilmPreloadRow.TimeLimitPaused("f2", "The Green Mile", TOTAL, heldBytes = 0L, wasActive = false)
        show { TvPreloadsPage(rows = listOf(row), heldFilms = emptyList(), onOpenTitle = {}, onCancel = {}, onRemove = {}, onResume = { resumed = it }) }
        compose.onNodeWithText("Queued").assertExists()
        compose.onNodeWithText("The Green Mile").performKeyInput { pressKey(Key.DirectionRight) }
        compose.onNodeWithText("Resume").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals(row, resumed)
    }

    /** M4: Back's own restoreKey lands the remote on the row a title was opened from, not on whatever now leads the list. */
    @Test
    fun restoreKeyLandsFocusOnTheNamedRowRatherThanTheFirst() {
        val rows = listOf(FilmPreloadRow.Waiting("f2", "The Green Mile", TOTAL), FilmPreloadRow.Waiting("f3", "Le Mans 66", TOTAL))
        show { TvPreloadsPage(rows = rows, heldFilms = emptyList(), onOpenTitle = {}, onCancel = {}, onRemove = {}, onResume = {}, restoreKey = "f3") }
        compose.onNodeWithText("Le Mans 66").assertIsFocused()
    }

    /** M4: a remote sitting on a row that is still present must not be pulled back to the front merely because some other row's own content changed. */
    @Test
    fun focusIsNotStolenWhenAnUnrelatedRowChangesUnderTheRemote() {
        var rows by mutableStateOf<List<FilmPreloadRow>>(
            listOf(
                FilmPreloadRow.Running("f1", "Der Pate", TOTAL, heldBytes = 10L, pauseReason = null),
                FilmPreloadRow.Waiting("f2", "The Green Mile", TOTAL),
            ),
        )
        show { TvPreloadsPage(rows = rows, heldFilms = emptyList(), onOpenTitle = {}, onCancel = {}, onRemove = {}, onResume = {}) }
        compose.onNodeWithText("Der Pate").assertIsFocused()
        compose.onNodeWithText("Der Pate").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("The Green Mile").assertIsFocused()

        // f1 finishes and leaves the list — f2 (still present) must keep the remote.
        rows = listOf(FilmPreloadRow.Waiting("f2", "The Green Mile", TOTAL))
        compose.waitForIdle()

        compose.onNodeWithText("The Green Mile").assertIsFocused()
    }

    /** As above, the other direction: the row that did hold focus leaving the page moves the remote to whatever is now first, rather than leaving nothing focused. */
    @Test
    fun focusMovesToTheFirstRowOnceTheFocusedRowItselfLeaves() {
        var rows by mutableStateOf<List<FilmPreloadRow>>(
            listOf(FilmPreloadRow.Waiting("f2", "The Green Mile", TOTAL), FilmPreloadRow.Waiting("f3", "Le Mans 66", TOTAL)),
        )
        show { TvPreloadsPage(rows = rows, heldFilms = emptyList(), onOpenTitle = {}, onCancel = {}, onRemove = {}, onResume = {}) }
        compose.onNodeWithText("The Green Mile").assertIsFocused()
        compose.onNodeWithText("The Green Mile").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText("Le Mans 66").assertIsFocused()

        // The row holding focus (Le Mans 66) is cancelled away.
        rows = listOf(FilmPreloadRow.Waiting("f2", "The Green Mile", TOTAL))
        compose.waitForIdle()

        compose.onNodeWithText("The Green Mile").assertIsFocused()
    }

    private companion object {
        /** 5 * 1024^3 — prints as "5.0 GB" through [model.humanSize]. */
        const val TOTAL = 5 * 1_073_741_824L
        const val HELD_40_PERCENT = TOTAL * 40 / 100
    }
}
