package ui.tv.catalog

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import model.ListOfSets
import model.MediaSet
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import ui.tv.setup.TvTextQuestionFieldTag
import kotlin.test.assertEquals

/**
 * [TvList]: one list's titles as a wall, the phone's actions over it, and
 * each title's own Remove. The delete confirmation opens its own `Dialog`
 * window, whose buttons and Back live in `TvConfirmDialogTest`; this only
 * checks it is asked in the phone's words.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w960dp-h540dp")
class TvListStateTest : TvScreenStateTest() {
    private val list = ListOfSets("a", "Sunday", listOf("film-0", "film-1"))

    @Test
    fun theTitlesAreAWallUnderTheListsNameAndTheFirstIsFocused() {
        var played: String? = null
        showList(films(2), onPlay = { played = it })

        compose.onNodeWithText("Sunday · 2").assertExists()
        compose.onNodeWithText("Film 0").assertIsFocused()
        compose.onNodeWithText("Film 1").performSemanticsAction(SemanticsActions.OnClick)
        assertEquals("film-1", played)
    }

    @Test
    fun removeTakesThatTitleOffAndNoOther() {
        var removed: String? = null
        showList(films(2), onRemove = { removed = it })

        compose.onAllNodesWithText("Remove")[1].performSemanticsAction(SemanticsActions.OnClick)

        assertEquals("film-1", removed)
    }

    @Test
    fun anEmptyListSaysThePhonesWordsAndLandsOnRename() {
        showList(emptyList())

        compose.onNodeWithText("Nothing on this list yet. Add titles from the player.").assertExists()
        compose.onNodeWithText("Rename").assertIsFocused()
        compose.onNodeWithText("Delete list").assertExists()
    }

    @Test
    fun renameAsksWithTheCurrentNameFilledIn() {
        var renamed: String? = null
        showList(films(1), onRename = { renamed = it })

        compose.onNodeWithText("Rename").performSemanticsAction(SemanticsActions.OnClick)
        compose.onNodeWithText("Sunday").assertExists()
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextClearance()
        compose.onNodeWithTag(TvTextQuestionFieldTag).performTextInput("Monday")
        compose.onNodeWithTag(TvTextQuestionFieldTag).performImeAction()

        assertEquals("Monday", renamed)
    }

    @Test
    fun deleteAsksFirstInThePhonesWords() {
        showList(films(1))

        compose.onNodeWithText("Delete list").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Delete \"Sunday\"?").assertExists()
        compose.onNodeWithText("The titles stay in the library.").assertExists()
    }

    private fun showList(
        sets: List<MediaSet>,
        onPlay: (String) -> Unit = {},
        onRename: (String) -> Unit = {},
        onRemove: (String) -> Unit = {},
    ) = show { TvList(list, sets, onPlay = onPlay, onRename = onRename, onDelete = {}, onRemove = onRemove) }
}
