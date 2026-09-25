package ui.tv.catalog

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import catalog.resumeLine
import model.Kind
import model.Progress
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import uniffi.mediagram_core.TitleInfo
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

    /** A remote has no way to scroll a page it has no stop in, so the overview is one. */
    @Test
    fun downFromPlayReachesALongOverviewAndUpReturns() {
        val overview = "A long synopsis. ".repeat(80).trim()
        val info = TitleInfo(overview = overview, tagline = null, genres = null, rating = null, network = null, status = null)
        show { TvTitlePage(set = film, info = info, progress = null, onPlay = {}) }
        compose.onNodeWithText("▶ Play").assertIsFocused()

        compose.onNodeWithText("▶ Play").performKeyInput { pressKey(Key.DirectionDown) }
        compose.onNodeWithText(overview).assertIsFocused()

        compose.onNodeWithText(overview).performKeyInput { pressKey(Key.DirectionUp) }
        compose.onNodeWithText("▶ Play").assertIsFocused()
    }

    /** The player's own rule: a glance at the opening, or a position in the credits, starts from the top. */
    @Test
    fun aPositionThePlayerWouldNotResumeFromSaysPlay() {
        for (at in listOf(5.0, 6760.0)) {
            show { TvTitlePage(set = film, info = null, progress = Progress("f", at = at, duration = 6780.0, updatedAt = 1), onPlay = {}) }
            compose.onNodeWithText("▶ Play").assertIsFocused()
            compose.onNodeWithText("▶ Resume").assertDoesNotExist()
            close()
        }
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
