package ui.tv.catalog

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import model.ListOfSets
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.setup.TvTextQuestionFieldTag
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** [TvLists]: the viewer's lists as rows, where the remote lands, and "New list" asking for a name. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvListsStateTest : TvScreenStateTest() {
    private val lists = listOf(ListOfSets("a", "Sunday", listOf("x")), ListOfSets("b", "Later", listOf("x", "y")))

    @Test
    fun eachListIsARowWithThePhonesCountAndTheFirstIsFocused() {
        show { TvLists(lists, onOpen = {}, onCreate = {}) }

        compose.onNodeWithText("Sunday · 1 title").assertIsFocused()
        compose.onNodeWithText("Later · 2 titles").assertExists()
        compose.onNodeWithText("＋ New list").assertExists()
    }

    @Test
    fun comingBackLandsOnTheListThatWasOpened() {
        show { TvLists(lists, onOpen = {}, onCreate = {}, restoreKey = "b") }

        compose.onNodeWithText("Later · 2 titles").assertIsFocused()
    }

    @Test
    fun noListsSaysSoAndLandsOnNewList() {
        show { TvLists(emptyList(), onOpen = {}, onCreate = {}) }

        compose.onNodeWithText("No lists yet.").assertExists()
        compose.onNodeWithText("＋ New list").assertIsFocused()
    }

    @Test
    fun newListAsksForANameAndCreatesItFromTheKeyboardsActionKey() {
        var created: String? = null
        show { TvLists(lists, onOpen = {}, onCreate = { created = it }) }

        compose.onNodeWithText("＋ New list").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Name for the list").assertExists()
        compose.onNodeWithTag(TvTextQuestionFieldTag).assertIsFocused()
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("Films for Friday")
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()

        assertEquals("Films for Friday", created)
        compose.onNodeWithText("Sunday · 1 title").assertIsFocused()
    }

    @Test
    fun aBlankNameIsNotAnAnswer() {
        var created: String? = null
        show { TvLists(lists, onOpen = {}, onCreate = { created = it }) }

        compose.onNodeWithText("＋ New list").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("   ")
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()

        assertNull(created)
        compose.onNodeWithText("Name for the list").assertExists()
    }
}
