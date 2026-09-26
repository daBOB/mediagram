package ui.tv.catalog

import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performSemanticsAction
import model.Credit
import model.Kind
import model.TitleCredits
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.test.assertEquals

/**
 * [TvTitlePage]'s own tabs — Overview/Cast/Similar/Details — this phase
 * adds: Cast and Similar are gated on there being something to show, the
 * editor's-choice toggle sits in Details, and a cast row opens a person.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvTitlePageTabsStateTest : TvScreenStateTest() {
    private val film = set("f", Kind.MOVIE, "A Film", addedAt = 0)

    @Test
    fun withNoCastOrSimilarOnlyOverviewAndDetailsAreOffered() {
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}) }

        listOf("Overview", "Details").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Cast").assertDoesNotExist()
        compose.onNodeWithText("Similar").assertDoesNotExist()
    }

    @Test
    fun castGetsItsOwnTabAndPressingAPersonOpensThem() {
        var opened: Long? = null
        val credits = TitleCredits(cast = listOf(Credit(personId = 7L, name = "Ada Actor", role = "Herself", portraitPath = null)), crew = emptyList())
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, credits = credits, onOpenPerson = { opened = it }) }

        compose.onNodeWithText("Cast").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Ada Actor").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(7L, opened)
    }

    @Test
    fun similarGetsItsOwnTabAndPressingATitleOpensIt() {
        var opened: String? = null
        val other = set("g", Kind.MOVIE, "Another Film", addedAt = 1)
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, similar = listOf(other), onOpenTitle = { opened = it }) }

        compose.onNodeWithText("Similar").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Another Film").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("g", opened)
    }

    @Test
    fun detailsOffersTheEditorsChoiceToggleUnlessItIsNull() {
        var toggled = false
        show {
            TvTitlePage(set = film, info = null, progress = null, onPlay = {}, editorsChoice = null, onToggleEditorsChoice = { toggled = true })
        }

        compose.onNodeWithText("Details").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Make editor's choice").performSemanticsAction(SemanticsActions.OnClick)

        assert(toggled)
    }

    /** A kids profile's `null` hides the row entirely — the same gate the phone's own screen applies. */
    @Test
    fun aKidsProfileHasNoEditorsChoiceRow() {
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, onToggleEditorsChoice = null) }

        compose.onNodeWithText("Details").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Make editor's choice").assertDoesNotExist()
    }

    /**
     * A page rebuilt fresh on the way back from a person's page (this page
     * is torn down while that one is shown, not kept alive underneath it)
     * still lands on Cast, and on that person, because [restoreKey] — not a
     * `rememberSaveable` this rebuild has nothing saved for — is what says so.
     */
    @Test
    fun comingBackFromAPersonLandsOnCastTabWithThatPersonFocused() {
        val credits =
            TitleCredits(
                cast =
                    listOf(
                        Credit(personId = 7L, name = "Ada Actor", role = "Herself", portraitPath = null),
                        Credit(personId = 8L, name = "Bo Actor", role = "Himself", portraitPath = null),
                    ),
                crew = emptyList(),
            )
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, credits = credits, restoreKey = "8") }

        compose.onNodeWithText("▶ Play").assertDoesNotExist()
        compose.onNodeWithText("Bo Actor").assertIsFocused()
    }

    /**
     * What the real library does on the way back: credits are looked up
     * again and arrive after the page is drawn, empty until then. The
     * restore must still land on the Cast tab and that person once they do.
     */
    @Test
    fun comingBackLandsOnCastEvenWhenCreditsArriveAfterThePage() {
        val arrived =
            TitleCredits(
                cast = listOf(Credit(personId = 8L, name = "Bo Actor", role = "Himself", portraitPath = null)),
                crew = emptyList(),
            )
        val credits = mutableStateOf(TitleCredits.Empty)
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, credits = credits.value, restoreKey = "8") }

        compose.runOnUiThread { credits.value = arrived }
        compose.waitForIdle()

        compose.onNodeWithText("▶ Play").assertDoesNotExist()
        compose.onNodeWithText("Bo Actor").assertIsFocused()
    }

    /** The same restore rule, for a similar film opened from the Similar tab. */
    @Test
    fun comingBackFromASimilarFilmLandsOnSimilarTabWithThatFilmFocused() {
        val other = set("g", Kind.MOVIE, "Another Film", addedAt = 1)
        show { TvTitlePage(set = film, info = null, progress = null, onPlay = {}, similar = listOf(other), restoreKey = "g") }

        compose.onNodeWithText("▶ Play").assertDoesNotExist()
        compose.onNodeWithText("Another Film").assertIsFocused()
    }
}
