package ui.tv.catalog

import androidx.compose.runtime.mutableStateListOf
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
    fun afterRemoveTheRemoteMovesToTheNextTitle() {
        showRemovable(films(3))

        compose.onAllNodesWithText("Remove")[0].performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Film 1").assertIsFocused()
    }

    @Test
    fun afterRemovingTheLastTitleTheRemoteMovesToTheOneBefore() {
        showRemovable(films(3))

        compose.onAllNodesWithText("Remove")[2].performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Film 1").assertIsFocused()
    }

    @Test
    fun afterRemovingTheOnlyTitleTheRemoteMovesToRename() {
        showRemovable(films(1))

        compose.onNodeWithText("Remove").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Rename").assertIsFocused()
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
    fun playAllStartsTheListAndIsOnlyOfferedWithSomethingToPlay() {
        var started = 0
        showList(films(2), onPlayAll = { started++ })

        compose.onNodeWithText("▶ Play all").performSemanticsAction(SemanticsActions.OnClick)

        assertEquals(1, started)
    }

    @Test
    fun backFromARunPlayAllStartedLandsOnPlayAll() {
        show { TvList(list, films(2), onPlay = {}, onRename = {}, onDelete = {}, onRemove = {}, restoreKey = TvPlayAllKey) }

        compose.onNodeWithText("▶ Play all").assertIsFocused()
    }

    /**
     * Back from Play all puts the remote on Play all, not on a plate; a
     * Remove after that still has to move it to the neighbouring title,
     * or the plate it was on goes and takes the focus with it.
     */
    @Test
    fun aRemoveAfterComingBackFromPlayAllMovesTheRemoteToTheNextTitle() {
        showRemovable(films(3), restoreKey = TvPlayAllKey)
        compose.onNodeWithText("▶ Play all").assertIsFocused()

        compose.onAllNodesWithText("Remove")[0].performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Film 1").assertIsFocused()
    }

    @Test
    fun anEmptyListOffersNoPlayAll() {
        showList(emptyList())

        compose.onNodeWithText("▶ Play all").assertDoesNotExist()
    }

    @Test
    fun deleteAsksFirstInThePhonesWords() {
        showList(films(1))

        compose.onNodeWithText("Delete list").performSemanticsAction(SemanticsActions.OnClick)

        compose.onNodeWithText("Delete \"Sunday\"?").assertExists()
        compose.onNodeWithText("The titles stay in the library.").assertExists()
    }

    /** A list whose Remove really takes the title off, as the ViewModel's next snapshot would. */
    private fun showRemovable(
        sets: List<MediaSet>,
        restoreKey: String? = null,
    ) {
        val held = mutableStateListOf(*sets.toTypedArray())
        show {
            TvList(list, held.toList(), onPlay = {}, onRename = {}, onDelete = {}, onRemove = { id -> held.removeAll { it.setId == id } }, restoreKey = restoreKey)
        }
    }

    private fun showList(
        sets: List<MediaSet>,
        onPlay: (String) -> Unit = {},
        onRename: (String) -> Unit = {},
        onRemove: (String) -> Unit = {},
        onPlayAll: () -> Unit = {},
    ) = show { TvList(list, sets, onPlay = onPlay, onRename = onRename, onDelete = {}, onRemove = onRemove, onPlayAll = onPlayAll) }
}
