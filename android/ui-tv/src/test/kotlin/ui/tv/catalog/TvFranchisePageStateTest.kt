package ui.tv.catalog

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.isFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.pressKey
import catalog.Franchise
import catalog.FranchisePage
import model.Kind
import model.WatchSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * [TvFranchisePage]: the web's franchise hero — the name, "two films ·
 * 1999–2003", the introduction inside it — over the films "In release
 * order"; where arrival lands, and that the introduction is a stop the
 * remote can reach to read.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvFranchisePageStateTest : TvScreenStateTest() {
    private val first = set("a1", Kind.MOVIE, "Adventure One", addedAt = 0, year = 1999)
    private val second = set("a2", Kind.MOVIE, "Adventure Two", addedAt = 1, year = 2003)
    private val saga = Franchise(id = 9L, name = "Adventure Saga", films = listOf(first, second), art = null)

    @Test
    fun theHeroNamesTheFranchiseItsFilmsAndTheirYearsAndCarriesItsIntroduction() {
        showPage(overview = "Two films about an adventure.")

        compose.onNodeWithTag(TvDepartmentHeroTitleTestTag).assertExists()
        compose.onNodeWithText("ADVENTURE SAGA").assertExists()
        compose.onNodeWithText("two films · 1999–2003").assertExists()
        compose.onNodeWithText("Two films about an adventure.").assertExists()
        compose.onNodeWithText("In release order").assertExists()
    }

    @Test
    fun noKnownYearsLeaveTheSpanOut() {
        val undated = Franchise(id = 9L, name = "Adventure Saga", films = listOf(first.copy(year = null), second.copy(year = 0)), art = null)
        show { TvFranchisePage(FranchisePage(undated, overview = null), WatchSnapshot.Empty, onOpenTitle = {}) }

        compose.onNodeWithText("two films").assertExists()
    }

    @Test
    fun arrivalLandsOnTheFirstFilmAndComingBackOnTheOneOpened() {
        var opened: String? = null
        showPage(overview = null, onOpenTitle = { opened = it })
        compose.onNodeWithText("Adventure One").assertIsFocused()
        compose.onNodeWithText("Adventure Two").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("a2", opened)
        close()

        showPage(overview = null, restoreKey = "a2")
        compose.onNodeWithText("Adventure Two").assertIsFocused()
    }

    /**
     * Opened fresh with an introduction, the remote rests on it — the page
     * shows from its top, the franchise's name in view — and Down is the
     * first film; Up from a film reads the introduction again.
     */
    @Test
    fun anIntroductionTakesTheRemoteFirstAndDownIsTheFirstFilm() {
        showPage(overview = "Two films about an adventure.")
        compose.onNodeWithText("Two films about an adventure.").assertIsFocused()

        press(Key.DirectionDown)
        compose.onNodeWithText("Adventure One").assertIsFocused()
        press(Key.DirectionRight)
        compose.onNodeWithText("Adventure Two").assertIsFocused()
        press(Key.DirectionUp)
        compose.onNodeWithText("Two films about an adventure.").assertIsFocused()
        press(Key.DirectionDown)
        compose.onNodeWithText("Adventure One").assertIsFocused()
    }

    /** Coming back from a film is not a fresh visit: the film opened takes the remote, introduction or not. */
    @Test
    fun comingBackWithAnIntroductionStillLandsOnTheFilmOpened() {
        showPage(overview = "Two films about an adventure.", restoreKey = "a2")

        compose.onNodeWithText("Adventure Two").assertIsFocused()
    }

    private fun showPage(
        overview: String?,
        restoreKey: String? = null,
        onOpenTitle: (String) -> Unit = {},
    ) {
        show { TvFranchisePage(FranchisePage(saga, overview), WatchSnapshot.Empty, onOpenTitle = onOpenTitle, restoreKey = restoreKey) }
    }

    private fun press(key: Key) {
        compose.onNode(isFocused()).performKeyInput { pressKey(key) }
        compose.waitForIdle()
    }
}
