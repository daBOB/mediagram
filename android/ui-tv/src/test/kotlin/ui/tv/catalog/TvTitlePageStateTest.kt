package ui.tv.catalog

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import catalog.resumeLine
import model.Kind
import model.Progress
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertTrue

/**
 * [TvTitlePage]: the facts beside the art, and Play — or Resume, with where
 * — holding the remote from the moment the page appears.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvTitlePageStateTest : TvScreenStateTest() {
    private val film = set("f", Kind.MOVIE, "A Film", addedAt = 0, year = 2004, durationSecs = 6780)

    @Test
    fun aTitleNotYetStartedOffersPlayFocusedBesideItsFacts() {
        var played = false
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = { played = true }) }

        compose.onNodeWithText("2004 · 1h 53m").assertExists()
        compose.onNodeWithText("▶ Play").assertIsFocused()
        compose.onNodeWithText("▶ Play").performSemanticsAction(SemanticsActions.OnClick)
        assertTrue(played)
    }

    @Test
    fun aTitleUnderwayOffersResumeAndSaysWhere() {
        val stopped = Progress("f", at = 750.0, duration = 6780.0, updatedAt = 1)
        show { TvTitlePage(set = film, info = null, progress = stopped, onPlay = {}) }

        compose.onNodeWithText(resumeLine(stopped)).assertExists()
        compose.onNodeWithText("▶ Resume").assertIsFocused()
        compose.onNodeWithText("▶ Play").assertDoesNotExist()
    }
}
